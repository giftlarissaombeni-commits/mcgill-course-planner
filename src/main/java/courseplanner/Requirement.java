package courseplanner;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A prerequisite expression such as {@code COMP 251 and (MATH 323 or MATH 324)}.
 *
 * <p>It is a small tree: leaves are single courses, inner nodes are AND / OR. That is what makes the
 * prerequisite structure more than a plain DAG: an OR node means "pick one of these".
 */
public sealed interface Requirement {

    /** True if the given set of completed course codes satisfies this requirement. */
    boolean isSatisfiedBy(Set<String> completed);

    /** Every course code mentioned anywhere in the expression, in order of appearance. */
    Set<String> mentionedCourses();

    /** No prerequisite at all. */
    record None() implements Requirement {
        @Override
        public boolean isSatisfiedBy(Set<String> completed) {
            return true;
        }

        @Override
        public Set<String> mentionedCourses() {
            return Set.of();
        }

        @Override
        public String toString() {
            return "none";
        }
    }

    /** A single course, e.g. "COMP 250". */
    record Needs(String code) implements Requirement {
        @Override
        public boolean isSatisfiedBy(Set<String> completed) {
            return completed.contains(code);
        }

        @Override
        public Set<String> mentionedCourses() {
            return Set.of(code);
        }

        @Override
        public String toString() {
            return code;
        }
    }

    /** All of the parts are required. */
    record AllOf(List<Requirement> parts) implements Requirement {
        public AllOf {
            parts = List.copyOf(parts);
        }

        @Override
        public boolean isSatisfiedBy(Set<String> completed) {
            return parts.stream().allMatch(p -> p.isSatisfiedBy(completed));
        }

        @Override
        public Set<String> mentionedCourses() {
            return parts.stream()
                    .flatMap(p -> p.mentionedCourses().stream())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        public String toString() {
            return parts.stream()
                    .map(p -> p instanceof AnyOf ? "(" + p + ")" : p.toString())
                    .collect(Collectors.joining(" and "));
        }
    }

    /** At least one of the parts is required. */
    record AnyOf(List<Requirement> parts) implements Requirement {
        public AnyOf {
            parts = List.copyOf(parts);
        }

        @Override
        public boolean isSatisfiedBy(Set<String> completed) {
            return parts.stream().anyMatch(p -> p.isSatisfiedBy(completed));
        }

        @Override
        public Set<String> mentionedCourses() {
            return parts.stream()
                    .flatMap(p -> p.mentionedCourses().stream())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        public String toString() {
            return parts.stream()
                    .map(p -> p instanceof AllOf ? "(" + p + ")" : p.toString())
                    .collect(Collectors.joining(" or "));
        }
    }
}
