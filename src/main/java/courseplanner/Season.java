package courseplanner;

/** The two regular terms the planner schedules into (summer is not modelled yet). */
public enum Season {
    FALL, WINTER;

    /** Parses the single-letter code used in the catalog file: F or W. */
    public static Season fromLetter(char letter) {
        switch (Character.toUpperCase(letter)) {
            case 'F':
                return FALL;
            case 'W':
                return WINTER;
            default:
                throw new IllegalArgumentException("Unknown term letter '" + letter + "' (use F or W)");
        }
    }
}
