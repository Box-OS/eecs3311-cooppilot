package ca.yorku.cooppilot.service;

import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.domain.Section;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LatexResumeParserTest {

    private static ParseResult result;

    @BeforeAll
    static void parseSample() throws Exception {
        result = new LatexResumeParser().parse(Files.readString(Path.of("samples/sample-resume.tex")));
    }

    private static List<ResumeBlock> in(Section s) {
        return result.blocks().stream().filter(b -> b.getSection() == s).toList();
    }

    @Test
    void findsEverySection() {
        assertEquals(1, in(Section.EDUCATION).size());
        assertEquals(2, in(Section.EXPERIENCE).size());
        assertEquals(2, in(Section.PROJECTS).size());
        assertEquals(1, in(Section.SKILLS).size());
        assertTrue(result.warnings().isEmpty(), () -> "unexpected warnings: " + result.warnings());
    }

    @Test
    void readsExperienceHeadingInRoleDatesCompanyOrder() {
        ResumeBlock tutor = in(Section.EXPERIENCE).get(0);
        assertEquals("Peer Tutor", tutor.getTitle());
        assertEquals("Lassonde Tutoring Centre", tutor.getSubtitle());
        assertEquals(YearMonth.of(2025, 9), tutor.getStartDate());
        assertTrue(tutor.isCurrent());
        assertEquals(2, tutor.getBullets().size());
        assertTrue(tutor.getBullets().get(0).contains("\\textbf{Java}"), "bullets keep their LaTeX");
    }

    @Test
    void readsEducationHeadingInSchoolLocationDegreeDatesOrder() {
        ResumeBlock edu = in(Section.EDUCATION).get(0);
        assertEquals("York University", edu.getTitle());
        assertTrue(edu.getSubtitle().startsWith("Bachelor of Engineering"));
        assertEquals(YearMonth.of(2023, 9), edu.getStartDate());
        assertEquals(YearMonth.of(2028, 4), edu.getEndDate());
    }

    @Test
    void readsProjectNameAndTechStackAsTags() {
        ResumeBlock p = in(Section.PROJECTS).get(0);
        assertEquals("StudyBuddy", p.getTitle());
        assertTrue(p.getTags().contains("react"));
        assertTrue(p.getTags().contains("postgresql"));
        ResumeBlock single = in(Section.PROJECTS).get(1);
        assertEquals(single.getStartDate(), single.getEndDate(), "a single date is a one-month project");
    }

    @Test
    void collectsSkillsFromLabelLines() {
        assertTrue(result.skills().containsAll(List.of("Java", "Spring Boot", "Docker")));
        assertEquals("Languages: Java, Python, C, JavaScript, SQL", in(Section.SKILLS).get(0).getBullets().get(0));
    }

    @Test
    void escapedPercentIsNotTreatedAsComment() {
        ResumeBlock p = in(Section.PROJECTS).get(0);
        assertTrue(p.getBullets().get(1).contains("[X=30\\%]"));
    }

    @Test
    void warnsInsteadOfFailingOnBadInput() {
        LatexResumeParser parser = new LatexResumeParser();
        assertFalse(parser.parse("").warnings().isEmpty());
        assertFalse(parser.parse("plain text, no sections").warnings().isEmpty());

        ParseResult odd = parser.parse("\\section{Hobbies} stuff \\section{Projects}"
                + "\\resumeProjectHeading{\\textbf{X} $|$ \\emph{Go}}{Someday}");
        assertTrue(odd.warnings().stream().anyMatch(w -> w.contains("Hobbies")));
        assertTrue(odd.warnings().stream().anyMatch(w -> w.contains("dates")));
        assertTrue(odd.warnings().stream().anyMatch(w -> w.contains("no bullet")));
    }
}
