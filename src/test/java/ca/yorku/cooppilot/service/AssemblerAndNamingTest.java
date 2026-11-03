package ca.yorku.cooppilot.service;

import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.domain.Section;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AssemblerAndNamingTest {

    @Test
    void stripsBoldInEducationAndSkillsButKeepsSkillLabels() {
        assertEquals("GPA 3.9, Dean's list", LatexAssembler.stripBold(Section.EDUCATION, "\\textbf{GPA 3.9}, Dean's list"));
        assertEquals("\\textbf{Languages}{: Java, SQL}",
                LatexAssembler.stripBold(Section.SKILLS, "\\textbf{Languages}{: \\textbf{Java}, SQL}"));
        assertEquals("\\textbf{Java}", LatexAssembler.stripBold(Section.EXPERIENCE, "\\textbf{Java}"));
    }

    @Test
    void ordersCurrentRolesFirstThenLatestEnd() {
        ResumeBlock old = block("Old", YearMonth.of(2022, 1), YearMonth.of(2022, 8));
        ResumeBlock now = block("Now", YearMonth.of(2025, 9), null);
        ResumeBlock recent = block("Recent", YearMonth.of(2023, 1), YearMonth.of(2025, 4));
        assertEquals(List.of("Now", "Recent", "Old"),
                LatexAssembler.orderReverseChronological(List.of(old, now, recent)).stream().map(ResumeBlock::getTitle).toList());
    }

    @Test
    void regeneratesBulletsUnderTheOriginalHeading() {
        ResumeBlock b = new ResumeBlock(null, Section.EXPERIENCE, "Tutor", "", null, null, List.of("new bullet"),
                "\\resumeSubheading{Tutor}{2025}{York}{Toronto}\n\\resumeItemListStart\n\\resumeItem{old}\n\\resumeItemListEnd");
        String tex = LatexAssembler.blockLatex(b);
        assertTrue(tex.contains("{Toronto}"), "location from the original heading survives");
        assertTrue(tex.contains("\\resumeItem{new bullet}"));
        assertFalse(tex.contains("old"));
    }

    @Test
    void fileNamesFollowCvCompanyRole() {
        ExportService e = new ExportService();
        assertEquals("CV_NorthwindLabs_SoftwareDeveloper", e.fileName("CV", "Northwind Labs", "Software Developer Co-op (Winter 2027)"));
        assertEquals("CV_CapitalOne_DataAnalyst", e.fileName("CV", "Capital One Canada", "Data Analyst Intern"));
        assertEquals("CL_RBC_Developer", e.fileName("CL", "Royal Bank of Canada", "Developer Co-op"));
    }

    private static ResumeBlock block(String title, YearMonth start, YearMonth end) {
        return new ResumeBlock(null, Section.EXPERIENCE, title, "", start, end, List.of(), "");
    }
}
