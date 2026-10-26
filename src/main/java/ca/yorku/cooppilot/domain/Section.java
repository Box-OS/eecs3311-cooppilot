package ca.yorku.cooppilot.domain;

import java.util.Locale;
import java.util.Optional;

/** Resume sections, in the default output order. */
public enum Section {
    EDUCATION, EXPERIENCE, PROJECTS, LEADERSHIP, SKILLS;

    /** Experience and Leadership must stay in reverse-chronological order; Projects may be ordered by relevance. */
    public boolean chronological() {
        return this == EXPERIENCE || this == LEADERSHIP;
    }

    /** Education and Skills never get bold words inside them. */
    public boolean allowsBold() {
        return this != EDUCATION && this != SKILLS;
    }

    /** Maps a LaTeX \section{...} heading to a section, e.g. "Technical Skills" -> SKILLS. */
    public static Optional<Section> fromHeading(String heading) {
        String h = heading.toLowerCase(Locale.ROOT);
        if (h.contains("education")) return Optional.of(EDUCATION);
        if (h.contains("experience") || h.contains("employment") || h.contains("work")) return Optional.of(EXPERIENCE);
        if (h.contains("project")) return Optional.of(PROJECTS);
        if (h.contains("leadership") || h.contains("activit") || h.contains("volunteer")) return Optional.of(LEADERSHIP);
        if (h.contains("skill")) return Optional.of(SKILLS);
        return Optional.empty();
    }
}
