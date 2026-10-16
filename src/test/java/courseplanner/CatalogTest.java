package courseplanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CatalogTest {

    private static final String HEADER = "code,title,credits,offered,prereq\n";

    private static Catalog load(String rows) throws IOException {
        return Catalog.fromCsv(new StringReader(HEADER + rows));
    }

    @Test
    void parsesRowsAndSkipsCommentsAndBlankLines() throws IOException {
        Catalog catalog = Catalog.fromCsv(new StringReader(
                "# a comment\n\n" + HEADER + "COMP 250,Intro,3,FW,\n# another\nCOMP 251,Algorithms,4,W,COMP 250\n"));
        assertEquals(2, catalog.size());
        Course c251 = catalog.get("COMP 251").orElseThrow();
        assertEquals(4, c251.credits());
        assertEquals(Set.of(Season.WINTER), c251.offered());
        assertEquals(new Requirement.Needs("COMP 250"), c251.prereq());
    }

    @Test
    void quotedFieldsMayContainCommas() throws IOException {
        Catalog catalog = load("COMP 250,\"Intro, with a comma\",3,FW,\n");
        assertEquals("Intro, with a comma", catalog.get("COMP 250").orElseThrow().title());
    }

    @Test
    void detectsCycles() throws IOException {
        Catalog catalog = load("COMP 100,A,3,FW,COMP 101\nCOMP 101,B,3,FW,COMP 102\nCOMP 102,C,3,FW,COMP 100\n");
        Optional<List<String>> cycle = catalog.findCycle();
        assertTrue(cycle.isPresent());
        List<String> path = cycle.get();
        assertEquals(path.get(0), path.get(path.size() - 1));
        assertEquals(4, path.size());
    }

    @Test
    void aCourseRequiringItselfIsACycle() throws IOException {
        assertTrue(load("COMP 100,A,3,FW,COMP 100\n").findCycle().isPresent());
    }

    @Test
    void acyclicCatalogHasNoCycle() throws IOException {
        Catalog catalog = load("COMP 100,A,3,FW,\nCOMP 101,B,3,FW,COMP 100\nCOMP 102,C,3,FW,COMP 100 and COMP 101\n");
        assertTrue(catalog.findCycle().isEmpty());
    }

    @Test
    void reportsPrerequisitesMissingFromTheCatalog() throws IOException {
        Catalog catalog = load("COMP 310,OS,3,FW,COMP 206 and COMP 273\n");
        assertEquals(Set.of("COMP 206", "COMP 273"), catalog.unknownReferences().get("COMP 310"));
    }

    @Test
    void rejectsBadRowsWithALineNumber() {
        IllegalArgumentException dup = assertThrows(IllegalArgumentException.class,
                () -> load("COMP 250,A,3,FW,\nCOMP 250,B,3,FW,\n"));
        assertTrue(dup.getMessage().contains("Line 3"), dup.getMessage());

        assertThrows(IllegalArgumentException.class, () -> load("COMP 250,A,three,FW,\n"));
        assertThrows(IllegalArgumentException.class, () -> load("COMP 250,A,3,X,\n"));
        assertThrows(IllegalArgumentException.class, () -> load("COMP 250,A,3,FW,COMP 100 and\n"));
        assertThrows(IllegalArgumentException.class, () -> load("COMP 250,A\n"));
    }

    @Test
    void bundledSampleCatalogIsHealthy() throws IOException {
        Catalog catalog = Catalog.fromResource("/courses.csv");
        assertTrue(catalog.size() > 10);
        assertTrue(catalog.findCycle().isEmpty());
        assertTrue(catalog.unknownReferences().isEmpty());
    }
}
