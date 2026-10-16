package courseplanner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * All known courses, plus the graph checks that go with them.
 *
 * <p>Edges run from a course to every course mentioned in its prerequisite expression.
 */
public final class Catalog {

    private static final int IN_PROGRESS = 1;
    private static final int DONE = 2;

    private final Map<String, Course> courses = new LinkedHashMap<>();

    public int size() {
        return courses.size();
    }

    public Collection<Course> all() {
        return Collections.unmodifiableCollection(courses.values());
    }

    /** Looks a course up by its canonical code (see {@link RequirementParser#normalizeCode}). */
    public Optional<Course> get(String code) {
        return Optional.ofNullable(courses.get(code));
    }

    void add(Course course) {
        if (courses.putIfAbsent(course.code(), course) != null) {
            throw new IllegalArgumentException("Duplicate course " + course.code());
        }
    }

    // ------------------------------------------------------------------ graph checks

    /**
     * Looks for a prerequisite cycle with a depth-first search that colours each course
     * unvisited / in progress / done. Meeting an "in progress" course again closes a cycle.
     * Runs in O(courses + prerequisite edges).
     *
     * @return the cycle as [A, B, ..., A], where each course requires the next one
     */
    public Optional<List<String>> findCycle() {
        Map<String, Integer> state = new HashMap<>();
        for (String code : courses.keySet()) {
            Optional<List<String>> cycle = visit(code, state, new ArrayList<>());
            if (cycle.isPresent()) {
                return cycle;
            }
        }
        return Optional.empty();
    }

    private Optional<List<String>> visit(String code, Map<String, Integer> state, List<String> path) {
        int s = state.getOrDefault(code, 0);
        if (s == DONE) {
            return Optional.empty();
        }
        if (s == IN_PROGRESS) {
            List<String> cycle = new ArrayList<>(path.subList(path.indexOf(code), path.size()));
            cycle.add(code);
            return Optional.of(cycle);
        }
        Course course = courses.get(code);
        if (course == null) {
            return Optional.empty(); // unknown courses are reported by unknownReferences()
        }
        state.put(code, IN_PROGRESS);
        path.add(code);
        for (String dep : course.prereq().mentionedCourses()) {
            Optional<List<String>> cycle = visit(dep, state, path);
            if (cycle.isPresent()) {
                return cycle;
            }
        }
        path.remove(path.size() - 1);
        state.put(code, DONE);
        return Optional.empty();
    }

    /** Prerequisites that name a course missing from the catalog, keyed by the course that names them. */
    public Map<String, Set<String>> unknownReferences() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Course course : courses.values()) {
            Set<String> unknown = new LinkedHashSet<>();
            for (String dep : course.prereq().mentionedCourses()) {
                if (!courses.containsKey(dep)) {
                    unknown.add(dep);
                }
            }
            if (!unknown.isEmpty()) {
                result.put(course.code(), unknown);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ loading

    public static Catalog fromFile(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return fromCsv(reader);
        }
    }

    public static Catalog fromResource(String resourcePath) throws IOException {
        try (InputStream in = Catalog.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            return fromCsv(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    /**
     * Reads {@code code,title,credits,offered,prereq} rows. Blank lines and lines starting with
     * '#' are ignored; the first remaining line is the header.
     */
    public static Catalog fromCsv(Reader reader) throws IOException {
        Catalog catalog = new Catalog();
        BufferedReader lines = new BufferedReader(reader);
        boolean headerSeen = false;
        int lineNo = 0;
        String line;
        while ((line = lines.readLine()) != null) {
            lineNo++;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (!headerSeen) {
                headerSeen = true;
                continue;
            }
            try {
                List<String> f = splitCsvLine(line);
                if (f.size() < 4 || f.size() > 5) {
                    throw new IllegalArgumentException(
                            "expected columns code,title,credits,offered,prereq (quote the prereq field if it contains commas)");
                }
                String code = RequirementParser.normalizeCode(f.get(0));
                Course course = new Course(
                        code,
                        f.get(1),
                        parseCredits(f.get(2)),
                        parseOffered(f.get(3)),
                        RequirementParser.parse(f.size() == 5 ? f.get(4) : ""));
                catalog.add(course);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Line " + lineNo + ": " + e.getMessage(), e);
            }
        }
        return catalog;
    }

    private static int parseCredits(String text) {
        try {
            int credits = Integer.parseInt(text.trim());
            if (credits < 1) {
                throw new IllegalArgumentException("credits must be at least 1");
            }
            return credits;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("credits must be a whole number, got '" + text + "'");
        }
    }

    private static Set<Season> parseOffered(String text) {
        if (text.isBlank()) {
            return EnumSet.allOf(Season.class);
        }
        Set<Season> seasons = EnumSet.noneOf(Season.class);
        for (char c : text.toCharArray()) {
            if (!Character.isWhitespace(c)) {
                seasons.add(Season.fromLetter(c));
            }
        }
        return seasons;
    }

    /** Splits one CSV line, honouring double-quoted fields and "" as an escaped quote. */
    static List<String> splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                fields.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        fields.add(current.toString().trim());
        return fields;
    }
}
