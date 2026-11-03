package ca.yorku.cooppilot.service;

import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.domain.Section;

import java.util.stream.Collectors;

/** Plain-text views of a resume, as an ATS would read it. */
public final class ResumeText {

    private ResumeText() {}

    /** Everything on the resume. */
    public static String all(Resume r) {
        if (r.getBlocks().isEmpty()) return LatexText.plain(r.getLatex());
        return r.getBlocks().stream().map(ResumeText::block).collect(Collectors.joining("\n"));
    }

    /** Only the text that shows evidence: experience, projects and leadership entries (not the skills line). */
    public static String evidence(Resume r) {
        return r.getBlocks().stream()
                .filter(b -> b.getSection() != Section.SKILLS && b.getSection() != Section.EDUCATION)
                .map(ResumeText::block).collect(Collectors.joining("\n"));
    }

    static String block(ResumeBlock b) {
        return LatexText.plain(b.getTitle() + " " + b.getSubtitle() + " " + String.join(" ", b.getBullets()));
    }
}
