package courseplanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MainTest {

    private record Result(int code, String out, String err) {
    }

    private static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Main.run(args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void planCommandBuildsAPlan() {
        Result r = run("plan", "--goal", "COMP 551", "--completed", "COMP 250,COMP 251,MATH 133,MATH 140",
                "--start", "winter-2027");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("Winter 2027"), r.out());
        assertTrue(r.out().contains("MATH 323"), r.out());
        assertTrue(r.out().contains("COMP 551"), r.out());
    }

    @Test
    void checkCommandPassesOnTheBundledCatalog() {
        Result r = run("check");
        assertEquals(0, r.code(), r.out());
        assertTrue(r.out().contains("No prerequisite cycles found."));
    }

    @Test
    void listCommandShowsCourses() {
        Result r = run("list");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("COMP 250"));
    }

    @Test
    void prereqsCommandShowsTheTree() {
        Result r = run("prereqs", "COMP", "551");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("MATH 323"), r.out());
        assertTrue(r.out().contains("MATH 140"), r.out());
    }

    @Test
    void dotCommandPrintsAGraph() {
        Result r = run("dot");
        assertEquals(0, r.code());
        assertTrue(r.out().startsWith("digraph"));
        assertTrue(r.out().contains("\"COMP 250\" -> \"COMP 251\";"));
    }

    @Test
    void unknownCommandIsExitCodeTwo() {
        assertEquals(2, run("frobnicate").code());
    }

    @Test
    void planWithoutAGoalIsAnError() {
        Result r = run("plan");
        assertEquals(1, r.code());
        assertTrue(r.err().contains("--goal"), r.err());
    }

    @Test
    void badCourseCodeIsAnErrorNotACrash() {
        Result r = run("plan", "--goal", "COMP 999");
        assertEquals(1, r.code());
        assertTrue(r.err().contains("COMP 999"), r.err());
    }
}
