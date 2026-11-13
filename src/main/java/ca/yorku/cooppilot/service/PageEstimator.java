package ca.yorku.cooppilot.service;

import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.domain.Section;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estimates how much of a letter page a Jake's-template resume fills, without compiling LaTeX:
 * each block costs its heading lines plus its bullets' wrapped lines.
 */
@Service
public class PageEstimator {

    static final double HEADER_LINES = 4.0;
    static final double SECTION_LINES = 1.6;
    static final int CHARS_PER_LINE = 100;

    private final int linesPerPage;

    public PageEstimator(@Value("${cooppilot.page.lines-per-page:63}") int linesPerPage) {
        this.linesPerPage = linesPerPage;
    }

    /** Fraction of one page (1.0 = exactly full). */
    public double estimate(Resume r) {
        double lines = HEADER_LINES;
        Set<Section> sections = EnumSet.noneOf(Section.class);
        for (ResumeBlock b : r.getBlocks()) {
            sections.add(b.getSection());
            lines += blockLines(b);
        }
        lines += sections.size() * SECTION_LINES;
        return lines / linesPerPage;
    }

    /** Condensing aims a little below a full page so small rendering differences cannot push it over. */
    public static final double TARGET = 0.98;

    /** Hard limit: nothing over one page can be saved. */
    public boolean fitsOnePage(Resume r) {
        return estimate(r) <= 1.0;
    }

    /** Condense goal (UC05): at most 98% of a page. */
    public boolean fitsTarget(Resume r) {
        return estimate(r) <= TARGET;
    }

    /** Lines one block takes: heading rows plus wrapped bullets. */
    public double blockLines(ResumeBlock b) {
        double heading = switch (b.getSection()) {
            case PROJECTS -> 1.2;
            case SKILLS -> 0.3;
            default -> 2.2;
        };
        double bullets = 0;
        for (String bullet : b.getBullets()) {
            int len = LatexText.plain(bullet).length();
            bullets += Math.max(1, Math.ceil(len / (double) CHARS_PER_LINE)) + 0.1;
        }
        return heading + bullets;
    }

    public int linesPerPage() {
        return linesPerPage;
    }
}
