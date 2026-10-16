package courseplanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RequirementParserTest {

    @Test
    void blankTextMeansNoRequirement() {
        assertInstanceOf(Requirement.None.class, RequirementParser.parse(""));
        assertInstanceOf(Requirement.None.class, RequirementParser.parse("   "));
        assertInstanceOf(Requirement.None.class, RequirementParser.parse(null));
        assertTrue(RequirementParser.parse("").isSatisfiedBy(Set.of()));
    }

    @Test
    void singleCourseIsNormalised() {
        assertEquals(new Requirement.Needs("COMP 250"), RequirementParser.parse("comp250"));
        assertEquals(new Requirement.Needs("COMP 250"), RequirementParser.parse("  COMP   250 "));
    }

    @Test
    void andBindsTighterThanOr() {
        Requirement r = RequirementParser.parse("COMP 250 or COMP 202 and MATH 133");
        assertTrue(r.isSatisfiedBy(Set.of("COMP 250")));
        assertFalse(r.isSatisfiedBy(Set.of("COMP 202")));
        assertTrue(r.isSatisfiedBy(Set.of("COMP 202", "MATH 133")));
    }

    @Test
    void parenthesesOverridePrecedence() {
        Requirement r = RequirementParser.parse("(COMP 250 or COMP 202) and MATH 133");
        assertFalse(r.isSatisfiedBy(Set.of("COMP 250")));
        assertTrue(r.isSatisfiedBy(Set.of("COMP 250", "MATH 133")));
        assertTrue(r.isSatisfiedBy(Set.of("COMP 202", "MATH 133")));
    }

    @Test
    void toStringRoundTrips() {
        String text = "COMP 251 and (MATH 323 or MATH 324)";
        assertEquals(text, RequirementParser.parse(text).toString());
    }

    @Test
    void listsMentionedCoursesInOrder() {
        Requirement r = RequirementParser.parse("COMP 251 and (MATH 323 or MATH 324) and COMP 250");
        assertEquals(List.of("COMP 251", "MATH 323", "MATH 324", "COMP 250"), List.copyOf(r.mentionedCourses()));
    }

    @Test
    void malformedInputIsRejected() {
        for (String bad : List.of("COMP 250 and", "(COMP 250", "COMP 250)", "COMP 250 MATH 133",
                "COMP 250, MATH 133", "COMP 25", "and COMP 250", "COMP 250 or or MATH 133")) {
            assertThrows(IllegalArgumentException.class, () -> RequirementParser.parse(bad), bad);
        }
    }

    @Test
    void normalizeCodeRejectsGarbage() {
        assertEquals("MATH 240", RequirementParser.normalizeCode("math240"));
        assertThrows(IllegalArgumentException.class, () -> RequirementParser.normalizeCode("MATH"));
    }
}
