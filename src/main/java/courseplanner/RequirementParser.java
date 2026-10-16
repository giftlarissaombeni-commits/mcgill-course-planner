package courseplanner;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recursive-descent parser for prerequisite text.
 *
 * <pre>
 * expr   := term ( "or" term )*
 * term   := atom ( "and" atom )*
 * atom   := COURSE | "(" expr ")"
 * COURSE := four letters, optional space, three digits   (COMP 250, comp250)
 * </pre>
 *
 * "and" binds tighter than "or", exactly like in boolean algebra. Blank text means no prerequisite.
 */
public final class RequirementParser {

    private static final Pattern TOKEN =
            Pattern.compile("(?i)\\(|\\)|\\band\\b|\\bor\\b|([a-z]{4})\\s*(\\d{3})");
    private static final Pattern CODE = Pattern.compile("(?i)([a-z]{4})\\s*(\\d{3})");

    private final List<String> tokens;
    private int pos = 0;

    private RequirementParser(List<String> tokens) {
        this.tokens = tokens;
    }

    public static Requirement parse(String text) {
        if (text == null || text.isBlank()) {
            return new Requirement.None();
        }
        RequirementParser parser = new RequirementParser(tokenize(text));
        Requirement result = parser.parseOr();
        if (parser.pos != parser.tokens.size()) {
            throw new IllegalArgumentException(
                    "Unexpected '" + parser.tokens.get(parser.pos) + "' in requirement: " + text);
        }
        return result;
    }

    /** Turns "comp250" or "COMP 250" into the canonical form "COMP 250". */
    public static String normalizeCode(String raw) {
        Matcher m = CODE.matcher(raw.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Not a course code: '" + raw + "' (expected something like COMP 250)");
        }
        return m.group(1).toUpperCase() + " " + m.group(2);
    }

    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher m = TOKEN.matcher(text);
        int i = 0;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                i++;
                continue;
            }
            m.region(i, text.length());
            if (!m.lookingAt()) {
                throw new IllegalArgumentException(
                        "Unexpected character '" + ch + "' at position " + i + " in requirement: " + text);
            }
            if (m.group(1) != null) {
                tokens.add(m.group(1).toUpperCase() + " " + m.group(2));
            } else {
                tokens.add(m.group().toLowerCase());
            }
            i = m.end();
        }
        return tokens;
    }

    private Requirement parseOr() {
        List<Requirement> parts = new ArrayList<>();
        parts.add(parseAnd());
        while (peekIs("or")) {
            pos++;
            parts.add(parseAnd());
        }
        return parts.size() == 1 ? parts.get(0) : new Requirement.AnyOf(parts);
    }

    private Requirement parseAnd() {
        List<Requirement> parts = new ArrayList<>();
        parts.add(parseAtom());
        while (peekIs("and")) {
            pos++;
            parts.add(parseAtom());
        }
        return parts.size() == 1 ? parts.get(0) : new Requirement.AllOf(parts);
    }

    private Requirement parseAtom() {
        if (pos >= tokens.size()) {
            throw new IllegalArgumentException("The requirement ended where a course or '(' was expected");
        }
        String token = tokens.get(pos++);
        if (token.equals("(")) {
            Requirement inner = parseOr();
            expect(")");
            return inner;
        }
        if (token.matches("[A-Z]{4} \\d{3}")) {
            return new Requirement.Needs(token);
        }
        throw new IllegalArgumentException("Expected a course or '(' but found '" + token + "'");
    }

    private boolean peekIs(String token) {
        return pos < tokens.size() && tokens.get(pos).equals(token);
    }

    private void expect(String token) {
        if (!peekIs(token)) {
            String found = pos < tokens.size() ? "found '" + tokens.get(pos) + "'" : "the requirement ended";
            throw new IllegalArgumentException("Expected '" + token + "' but " + found);
        }
        pos++;
    }
}
