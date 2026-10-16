package courseplanner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Turns "here is what I've done, here is what I want" into a term-by-term plan.
 *
 * <p>Two phases:
 * <ol>
 *   <li><b>Resolve</b>: walk the prerequisite expressions backwards from the goals and collect every
 *       course that still has to be taken. For an OR node the cheapest alternative wins (fewest extra
 *       courses, counting courses we already need anyway as free).</li>
 *   <li><b>Schedule</b>: fill terms one at a time. A course may be taken once all its prerequisites
 *       were completed in an <i>earlier</i> term and it is offered in that term's season. When more
 *       courses are eligible than fit under the credit cap, the ones that unlock the longest chain of
 *       follow-up courses go first (critical-path list scheduling).</li>
 * </ol>
 *
 * <p>The schedule is a heuristic, not a proven optimum: minimising the number of terms under a credit
 * cap is a hard scheduling problem. In practice, putting long chains first gives short plans.
 */
public final class Planner {

    public record Options(int maxCreditsPerTerm, Term start, int maxTerms) {
        public Options {
            if (maxCreditsPerTerm < 1) {
                throw new IllegalArgumentException("max credits per term must be at least 1");
            }
            if (maxTerms < 1) {
                throw new IllegalArgumentException("max terms must be at least 1");
            }
            Objects.requireNonNull(start, "start");
        }
    }

    public record TermPlan(Term term, List<Course> courses) {
        public int credits() {
            return courses.stream().mapToInt(Course::credits).sum();
        }
    }

    /**
     * @param terms       the terms that have courses in them, in order
     * @param unscheduled courses that are needed but could not be placed within the limits
     * @param external    prerequisites that are not in the catalog and not marked completed
     * @param alreadyDone goals that were already completed
     */
    public record Plan(
            List<TermPlan> terms, Set<String> unscheduled, Set<String> external, Set<String> alreadyDone) {

        public boolean isComplete() {
            return unscheduled.isEmpty() && external.isEmpty();
        }
    }

    private final Catalog catalog;

    public Planner(Catalog catalog) {
        this.catalog = Objects.requireNonNull(catalog);
    }

    public Plan plan(Set<String> completed, Collection<String> goals, Options options) {
        Optional<List<String>> cycle = catalog.findCycle();
        if (cycle.isPresent()) {
            throw new IllegalStateException("Prerequisite cycle in catalog: " + String.join(" -> ", cycle.get()));
        }

        Set<String> have = new LinkedHashSet<>();
        for (String code : completed) {
            have.add(RequirementParser.normalizeCode(code));
        }

        // Phase 1: which courses are still needed?
        Set<String> needed = new LinkedHashSet<>();
        Set<String> external = new LinkedHashSet<>();
        Set<String> alreadyDone = new LinkedHashSet<>();
        for (String goal : goals) {
            String code = RequirementParser.normalizeCode(goal);
            if (have.contains(code)) {
                alreadyDone.add(code);
                continue;
            }
            if (catalog.get(code).isEmpty()) {
                throw new IllegalArgumentException("Unknown goal course: " + code);
            }
            resolve(new Requirement.Needs(code), have, needed, external);
        }

        // Phase 2: place them into terms.
        Map<String, Integer> height = new HashMap<>();
        for (String code : needed) {
            height(code, needed, height);
        }

        Set<String> done = new HashSet<>(have);
        Set<String> remaining = new LinkedHashSet<>(needed);
        List<TermPlan> terms = new ArrayList<>();
        Term term = options.start();
        int idleTerms = 0;

        for (int i = 0; i < options.maxTerms() && !remaining.isEmpty() && idleTerms < 2; i++) {
            final Term current = term;
            term = term.next();

            List<Course> eligible = remaining.stream()
                    .map(code -> catalog.get(code).orElseThrow())
                    .filter(c -> c.offeredIn(current.season()))
                    .filter(c -> c.prereq().isSatisfiedBy(done))
                    .sorted(Comparator.comparingInt((Course c) -> -height.get(c.code()))
                            .thenComparing(Course::code))
                    .toList();

            List<Course> chosen = new ArrayList<>();
            int credits = 0;
            for (Course c : eligible) {
                if (credits + c.credits() <= options.maxCreditsPerTerm()) {
                    chosen.add(c);
                    credits += c.credits();
                }
            }

            if (chosen.isEmpty()) {
                // Two idle terms in a row cover both seasons, so nothing can ever change: stop.
                idleTerms++;
                continue;
            }
            idleTerms = 0;
            for (Course c : chosen) {
                done.add(c.code());
                remaining.remove(c.code());
            }
            terms.add(new TermPlan(current, List.copyOf(chosen)));
        }

        return new Plan(List.copyOf(terms), remaining, external, alreadyDone);
    }

    // ------------------------------------------------------------------ phase 1

    private void resolve(Requirement req, Set<String> have, Set<String> needed, Set<String> external) {
        if (req instanceof Requirement.None) {
            return;
        }
        if (req instanceof Requirement.Needs n) {
            requireCourse(n.code(), have, needed, external);
        } else if (req instanceof Requirement.AllOf all) {
            for (Requirement part : all.parts()) {
                resolve(part, have, needed, external);
            }
        } else if (req instanceof Requirement.AnyOf any) {
            Set<String> bestNeeded = null;
            Set<String> bestExternal = null;
            int bestCost = Integer.MAX_VALUE;
            for (Requirement alternative : any.parts()) {
                Set<String> n = new LinkedHashSet<>(needed);
                Set<String> e = new LinkedHashSet<>(external);
                resolve(alternative, have, n, e);
                // Missing outside courses are far worse than a few extra catalog courses.
                int cost = (e.size() - external.size()) * 1000 + (n.size() - needed.size());
                if (cost < bestCost) {
                    bestCost = cost;
                    bestNeeded = n;
                    bestExternal = e;
                }
            }
            needed.clear();
            needed.addAll(bestNeeded);
            external.clear();
            external.addAll(bestExternal);
        }
    }

    private void requireCourse(String code, Set<String> have, Set<String> needed, Set<String> external) {
        if (have.contains(code) || needed.contains(code) || external.contains(code)) {
            return;
        }
        Optional<Course> course = catalog.get(code);
        if (course.isEmpty()) {
            external.add(code);
            return;
        }
        needed.add(code);
        resolve(course.get().prereq(), have, needed, external);
    }

    // ------------------------------------------------------------------ phase 2

    /** Length of the longest chain of needed courses that starts at this one (itself included). */
    private int height(String code, Set<String> needed, Map<String, Integer> memo) {
        Integer known = memo.get(code);
        if (known != null) {
            return known;
        }
        int longestAfter = 0;
        for (String other : needed) {
            Course dependent = catalog.get(other).orElseThrow();
            if (dependent.prereq().mentionedCourses().contains(code)) {
                longestAfter = Math.max(longestAfter, height(other, needed, memo));
            }
        }
        memo.put(code, longestAfter + 1);
        return longestAfter + 1;
    }
}
