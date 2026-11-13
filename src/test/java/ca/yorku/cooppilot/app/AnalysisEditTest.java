package ca.yorku.cooppilot.app;

import ca.yorku.cooppilot.domain.Keyword;
import ca.yorku.cooppilot.domain.Posting;
import ca.yorku.cooppilot.llm.LLMClient;
import ca.yorku.cooppilot.llm.MockLlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** F02: deadline extraction and manual / edited analysis (UC02 3a). */
@SpringBootTest(classes = CoopPilotApplication.class)
class AnalysisEditTest {

    @Autowired CoopPilotFacade facade;
    @Autowired LLMClient llm;

    @Test
    void deadlineIsReadFromTheAnalysis() {
        ((MockLlmClient) llm).enqueueText("{\"company\":\"Acme\",\"role\":\"Dev\",\"deadline\":\"2026-12-01\","
                + "\"mustHaves\":[\"Java\"],\"keywords\":[\"Java\"],\"documentsRequired\":[\"Resume\"]}");
        Posting p = facade.analyzePosting(facade.importPosting("Acme posting, apply by Dec 1", "", "").getId());
        assertEquals(LocalDate.of(2026, 12, 1), facade.posting(p.getId()).getDeadline().orElseThrow());
    }

    @Test
    void analysisCanBeFilledInByHandWhenTheLlmFails() {
        Posting p = facade.importPosting("Some posting the model could not read", "", "");
        facade.saveAnalysis(p.getId(), "Lakeshore Bank", "Data Analyst Co-op", "Winter 2027", "2026-11-30",
                "SQL\nExcellent communication\nTeamwork", "Python", "SQL | MySQL, PostgreSQL | must\nPython\nTableau | | must",
                "Resume\nCover Letter");
        Posting saved = facade.posting(p.getId());
        assertTrue(saved.isAnalyzed());
        assertEquals("Lakeshore Bank", saved.getCompany());
        var a = saved.getAnalysis().orElseThrow();
        Keyword sql = a.keywords().get(0);
        assertTrue(sql.mustHave());
        assertTrue(sql.aliases().contains("PostgreSQL"));
        assertFalse(a.keywords().get(1).mustHave());
        assertTrue(a.keywords().get(2).mustHave());
        assertTrue(a.softSkillFocus(), "2 of 3 must-haves are soft skills");
        assertTrue(a.requiresCoverLetter());
        assertEquals(LocalDate.of(2026, 11, 30), saved.getDeadline().orElseThrow());
    }

    @Test
    void badManualInputIsRejected() {
        Posting p = facade.importPosting("text", "X", "Y");
        assertThrows(IllegalArgumentException.class,
                () -> facade.saveAnalysis(p.getId(), "", "", "", "", "Java", "", "", ""), "no keywords");
        assertThrows(IllegalArgumentException.class,
                () -> facade.saveAnalysis(p.getId(), "", "", "", "next week", "Java", "", "Java", ""), "bad date");
    }
}
