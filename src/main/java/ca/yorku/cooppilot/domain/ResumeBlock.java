package ca.yorku.cooppilot.domain;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One entry of a resume (a job, project, degree or the skills list) with its heading lines and bullets.
 * {@code latex} keeps the original Jake's-template source so the block can be reassembled exactly.
 * A null {@code endDate} means "Present".
 */
public class ResumeBlock {

    private final String id;
    private final Section section;
    private String title;
    private String subtitle;
    private YearMonth startDate;
    private YearMonth endDate;
    private final List<String> bullets = new ArrayList<>();
    private final Set<String> tags = new LinkedHashSet<>();
    private String latex;

    public ResumeBlock(String id, Section section, String title, String subtitle,
                       YearMonth startDate, YearMonth endDate, List<String> bullets, String latex) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        this.section = Objects.requireNonNull(section, "section");
        this.title = title == null ? "" : title.trim();
        this.subtitle = subtitle == null ? "" : subtitle.trim();
        this.startDate = startDate;
        this.endDate = endDate;
        if (bullets != null) this.bullets.addAll(bullets);
        this.latex = latex == null ? "" : latex;
    }

    public boolean isCurrent() {
        return startDate != null && endDate == null;
    }

    /** Sort key for reverse-chronological order: current roles first, then the latest end date. */
    public YearMonth sortDate() {
        if (isCurrent()) return YearMonth.of(9999, 12);
        if (endDate != null) return endDate;
        return startDate == null ? YearMonth.of(1, 1) : startDate;
    }

    public ResumeBlock copy() {
        ResumeBlock b = new ResumeBlock(id, section, title, subtitle, startDate, endDate, bullets, latex);
        b.tags.addAll(tags);
        return b;
    }

    public String getId() { return id; }
    public Section getSection() { return section; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubtitle() { return subtitle; }
    public YearMonth getStartDate() { return startDate; }
    public YearMonth getEndDate() { return endDate; }
    public void setDates(YearMonth start, YearMonth end) { this.startDate = start; this.endDate = end; }
    public List<String> getBullets() { return List.copyOf(bullets); }
    public void setBullets(List<String> newBullets) { bullets.clear(); bullets.addAll(newBullets); }
    public Set<String> getTags() { return Set.copyOf(tags); }
    public void addTag(String t) { if (t != null && !t.isBlank()) tags.add(t.trim().toLowerCase()); }
    public String getLatex() { return latex; }
    public void setLatex(String latex) { this.latex = latex; }

    @Override
    public String toString() {
        return section + ": " + title;
    }
}
