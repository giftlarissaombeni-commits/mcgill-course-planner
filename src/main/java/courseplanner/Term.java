package courseplanner;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One academic term, labelled by its calendar year: Fall 2026 is followed by Winter 2027,
 * which is followed by Fall 2027, and so on.
 */
public record Term(Season season, int year) {

    private static final Pattern FORMAT = Pattern.compile("(?i)(fall|winter)[\\s-]*(\\d{4})");

    public Term next() {
        return season == Season.FALL
                ? new Term(Season.WINTER, year + 1)
                : new Term(Season.FALL, year);
    }

    /** Parses text such as "fall-2026" or "Winter 2027". */
    public static Term parse(String text) {
        Matcher m = FORMAT.matcher(text.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException(
                    "Bad term '" + text + "' (expected something like fall-2026 or winter-2027)");
        }
        Season season = m.group(1).equalsIgnoreCase("fall") ? Season.FALL : Season.WINTER;
        return new Term(season, Integer.parseInt(m.group(2)));
    }

    /** The next regular term to plan for, given today's date. */
    public static Term upcoming(LocalDate today) {
        return today.getMonthValue() >= 9
                ? new Term(Season.WINTER, today.getYear() + 1)
                : new Term(Season.FALL, today.getYear());
    }

    @Override
    public String toString() {
        return (season == Season.FALL ? "Fall " : "Winter ") + year;
    }
}
