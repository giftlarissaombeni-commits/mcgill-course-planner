package courseplanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlannerTest {

    private static final String CSV = """
            code,title,credits,offered,prereq
            COMP 250,Intro,3,FW,
            MATH 240,Discrete,3,FW,
            COMP 251,Algorithms,3,FW,COMP 250 and MATH 240
            COMP 360,Algorithm Design,3,W,COMP 251
            MATH 323,Probability,3,FW,
            COMP 424,AI,3,F,COMP 251 and MATH 323
            COMP 551,ML,4,FW,COMP 251 and MATH 323
            COMP 579,RL,4,W,COMP 424 or COMP 551
            """;

    private static final Term FALL_2026 = new Term(Season.FALL, 2026);
    private static final Term WINTER_2027 = new Term(Season.WINTER, 2027);

    private static Catalog catalog() throws IOException {
        return Catalog.fromCsv(new StringReader(CSV));
    }

    private static Planner.Options options(int credits, Term start) {
        return new Planner.Options(credits, start, 12);
    }

    private static List<String> codes(Planner.Plan plan) {
        List<String> codes = new ArrayList<>();
        plan.terms().forEach(t -> t.courses().forEach(c -> codes.add(c.code())));
        return codes;
    }

    @Test
    void prerequisitesComeEarlierAndSeasonsAreRespected() throws IOException {
        Catalog catalog = catalog();
        Planner.Plan plan = new Planner(catalog)
                .plan(Set.of(), List.of("COMP 360", "COMP 424"), options(15, FALL_2026));

        assertTrue(plan.isComplete());
        Map<String, Integer> termIndex = new HashMap<>();
        for (int i = 0; i < plan.terms().size(); i++) {
            Planner.TermPlan termPlan = plan.terms().get(i);
            for (Course c : termPlan.courses()) {
                termIndex.put(c.code(), i);
                assertTrue(c.offeredIn(termPlan.term().season()),
                        c.code() + " is not offered in " + termPlan.term());
            }
        }
        assertTrue(termIndex.containsKey("COMP 360"));
        assertTrue(termIndex.containsKey("COMP 424"));
        for (Course c : catalog.all()) {
            Integer index = termIndex.get(c.code());
            if (index == null) {
                continue;
            }
            for (String dep : c.prereq().mentionedCourses()) {
                Integer depIndex = termIndex.get(dep);
                if (depIndex != null) {
                    assertTrue(depIndex < index, dep + " must come before " + c.code());
                }
            }
        }
    }

    @Test
    void skipsAheadOverTermsWhereNothingIsOffered() throws IOException {
        // COMP 360 is winter-only, so after Fall 2026 and Winter 2027 it waits for Winter 2028.
        Planner.Plan plan = new Planner(catalog())
                .plan(Set.of(), List.of("COMP 360"), options(15, FALL_2026));
        assertEquals(3, plan.terms().size());
        assertEquals(new Term(Season.WINTER, 2028), plan.terms().get(2).term());
        assertEquals(List.of("COMP 360"), plan.terms().get(2).courses().stream().map(Course::code).toList());
    }

    @Test
    void respectsTheCreditCap() throws IOException {
        Planner.Plan plan = new Planner(catalog())
                .plan(Set.of(), List.of("COMP 551"), options(6, FALL_2026));
        assertTrue(plan.isComplete());
        assertEquals(5, codes(plan).size());
        for (Planner.TermPlan termPlan : plan.terms()) {
            assertTrue(termPlan.credits() <= 6, termPlan.term() + " has " + termPlan.credits() + " credits");
        }
    }

    @Test
    void longestChainsAreScheduledFirst() throws IOException {
        // With room for only two courses, the 250 -> 251 -> 551 chain must start before MATH 323.
        Planner.Plan plan = new Planner(catalog())
                .plan(Set.of(), List.of("COMP 551"), options(6, FALL_2026));
        List<String> firstTerm = plan.terms().get(0).courses().stream().map(Course::code).toList();
        assertEquals(List.of("COMP 250", "MATH 240"), firstTerm);
    }

    @Test
    void doesNotRetakeCompletedCourses() throws IOException {
        Planner.Plan plan = new Planner(catalog())
                .plan(Set.of("COMP 250", "MATH 240"), List.of("COMP 251"), options(15, FALL_2026));
        assertEquals(List.of("COMP 251"), codes(plan));
        assertEquals(1, plan.terms().size());
    }

    @Test
    void acceptsLooselyFormattedCourseCodes() throws IOException {
        Planner.Plan plan = new Planner(catalog())
                .plan(Set.of("comp250", "math 240"), List.of("comp 251"), options(15, FALL_2026));
        assertEquals(List.of("COMP 251"), codes(plan));
    }

    @Test
    void aGoalThatIsAlreadyDoneNeedsNoTerms() throws IOException {
        Planner.Plan plan = new Planner(catalog())
                .plan(Set.of("COMP 250"), List.of("COMP 250"), options(15, FALL_2026));
        assertTrue(plan.terms().isEmpty());
        assertEquals(Set.of("COMP 250"), plan.alreadyDone());
        assertTrue(plan.isComplete());
    }

    @Test
    void reusesAnAlternativeThatIsAlreadyCompleted() throws IOException {
        Set<String> completed = Set.of("COMP 250", "MATH 240", "COMP 251", "MATH 323", "COMP 424");
        Planner.Plan plan = new Planner(catalog())
                .plan(completed, List.of("COMP 579"), options(15, WINTER_2027));
        assertEquals(List.of("COMP 579"), codes(plan));
    }

    @Test
    void prefersTheAlternativeThatIsAlreadyNeededForAnotherGoal() throws IOException {
        // COMP 551 is a goal anyway, so COMP 579 must not drag in COMP 424 as well.
        Planner.Plan plan = new Planner(catalog())
                .plan(Set.of(), List.of("COMP 551", "COMP 579"), options(15, FALL_2026));
        assertTrue(plan.isComplete());
        assertFalse(codes(plan).contains("COMP 424"));
        assertTrue(codes(plan).contains("COMP 551"));
        assertTrue(codes(plan).contains("COMP 579"));
    }

    @Test
    void unknownGoalIsRejected() throws IOException {
        assertThrows(IllegalArgumentException.class,
                () -> new Planner(catalog()).plan(Set.of(), List.of("COMP 999"), options(15, FALL_2026)));
    }

    @Test
    void reportsPrerequisitesThatLiveOutsideTheCatalog() throws IOException {
        Catalog small = Catalog.fromCsv(new StringReader(
                "code,title,credits,offered,prereq\nCOMP 310,OS,3,FW,COMP 206 and COMP 273\n"));
        Planner.Plan plan = new Planner(small).plan(Set.of(), List.of("COMP 310"), options(15, FALL_2026));
        assertEquals(Set.of("COMP 206", "COMP 273"), plan.external());
        assertEquals(Set.of("COMP 310"), plan.unscheduled());
        assertFalse(plan.isComplete());
    }

    @Test
    void completedExternalPrerequisitesUnblockTheCourse() throws IOException {
        Catalog small = Catalog.fromCsv(new StringReader(
                "code,title,credits,offered,prereq\nCOMP 310,OS,3,FW,COMP 206 and COMP 273\n"));
        Planner.Plan plan = new Planner(small)
                .plan(Set.of("COMP 206", "COMP 273"), List.of("COMP 310"), options(15, FALL_2026));
        assertTrue(plan.isComplete());
        assertEquals(List.of("COMP 310"), codes(plan));
    }

    @Test
    void refusesToPlanOverACyclicCatalog() throws IOException {
        Catalog cyclic = Catalog.fromCsv(new StringReader(
                "code,title,credits,offered,prereq\nCOMP 100,A,3,FW,COMP 101\nCOMP 101,B,3,FW,COMP 100\n"));
        assertThrows(IllegalStateException.class,
                () -> new Planner(cyclic).plan(Set.of(), List.of("COMP 100"), options(15, FALL_2026)));
    }

    @Test
    void courseLargerThanTheCapIsReportedNotLooped() throws IOException {
        Planner.Plan plan = new Planner(catalog())
                .plan(Set.of("COMP 250", "MATH 240", "COMP 251", "MATH 323"), List.of("COMP 551"),
                        options(3, FALL_2026));
        assertFalse(plan.isComplete());
        assertEquals(Set.of("COMP 551"), plan.unscheduled());
    }
}
