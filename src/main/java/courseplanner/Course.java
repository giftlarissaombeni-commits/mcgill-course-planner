package courseplanner;

import java.util.Set;

/**
 * One catalog entry.
 *
 * @param code    canonical code, e.g. "COMP 251"
 * @param offered the terms the course normally runs in
 * @param prereq  what must be completed in an earlier term
 */
public record Course(String code, String title, int credits, Set<Season> offered, Requirement prereq) {

    public Course {
        offered = Set.copyOf(offered);
    }

    public boolean offeredIn(Season season) {
        return offered.contains(season);
    }

    /** "F", "W" or "FW". */
    public String offeredLabel() {
        return (offered.contains(Season.FALL) ? "F" : "") + (offered.contains(Season.WINTER) ? "W" : "");
    }
}
