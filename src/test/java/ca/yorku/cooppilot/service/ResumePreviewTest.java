package ca.yorku.cooppilot.service;

import ca.yorku.cooppilot.domain.Resume;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ResumePreviewTest {

    @Test
    void latexBecomesSafeHtml() {
        assertEquals("Tutored <strong>20+</strong> students in <em>Java</em> &amp; C – 30%",
                LatexText.toHtml("Tutored \\textbf{20+} students in \\emph{Java} \\& C -- 30\\%"));
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;", LatexText.toHtml("<script>alert(1)</script>"),
                "user text can never inject HTML");
    }

    @Test
    void previewOfTheSampleResume() throws Exception {
        String tex = Files.readString(Path.of("samples/sample-resume.tex"));
        Resume r = new Resume(null, "CV", "", Resume.Kind.ORIGINAL, new LatexResumeParser().parse(tex).blocks(), tex);

        ResumePreview.Preview p = new ResumePreview().build(r);

        assertEquals("Alex Chen", p.name());
        assertTrue(p.contact().contains("alex.chen@example.com"));
        assertEquals(java.util.List.of("Education", "Experience", "Projects", "Technical Skills"),
                p.sections().stream().map(ResumePreview.SectionView::heading).toList());
        ResumePreview.Entry tutor = p.sections().get(1).entries().get(0);
        assertEquals("Peer Tutor", tutor.title());
        assertEquals("Sep 2025 – Present", tutor.dates());
        assertTrue(tutor.bullets().get(0).contains("<strong>Java</strong>"));
        assertTrue(p.sections().get(3).skillLines().get(0).startsWith("<strong>Languages</strong>: Java"));
    }
}
