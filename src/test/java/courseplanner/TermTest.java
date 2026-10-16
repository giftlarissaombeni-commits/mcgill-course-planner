package courseplanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TermTest {

    @Test
    void parsesCommonSpellings() {
        assertEquals(new Term(Season.FALL, 2026), Term.parse("fall-2026"));
        assertEquals(new Term(Season.WINTER, 2027), Term.parse("Winter 2027"));
    }

    @Test
    void rejectsUnknownSeasons() {
        assertThrows(IllegalArgumentException.class, () -> Term.parse("spring-2026"));
    }

    @Test
    void fallIsFollowedByWinterOfTheNextCalendarYear() {
        Term fall = new Term(Season.FALL, 2026);
        assertEquals(new Term(Season.WINTER, 2027), fall.next());
        assertEquals(new Term(Season.FALL, 2027), fall.next().next());
    }

    @Test
    void upcomingTermDependsOnTheMonth() {
        assertEquals(new Term(Season.WINTER, 2027), Term.upcoming(LocalDate.of(2026, 10, 15)));
        assertEquals(new Term(Season.FALL, 2027), Term.upcoming(LocalDate.of(2027, 2, 1)));
    }
}
