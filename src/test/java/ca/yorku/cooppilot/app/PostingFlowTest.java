package ca.yorku.cooppilot.app;

import ca.yorku.cooppilot.domain.Posting;
import ca.yorku.cooppilot.llm.LLMClient;
import ca.yorku.cooppilot.llm.MockLlmClient;
import ca.yorku.cooppilot.llm.PostingAnalysisTaskTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** F02 through the facade: import, analyze with the mock LLM, reload from SQLite. */
@SpringBootTest(classes = CoopPilotApplication.class)
class PostingFlowTest {

    @Autowired CoopPilotFacade facade;
    @Autowired LLMClient llm;

    @Test
    void importAnalyzeAndReload() throws Exception {
        ((MockLlmClient) llm).enqueueText(PostingAnalysisTaskTest.GOOD);
        Posting p = facade.importPosting(Files.readString(Path.of("samples/sample-posting.txt")), "", "");
        assertFalse(p.isAnalyzed());

        facade.analyzePosting(p.getId());
        Posting reloaded = facade.posting(p.getId());
        assertTrue(reloaded.isAnalyzed());
        assertEquals("Northwind Labs", reloaded.getCompany(), "blank company filled from the analysis");
        assertEquals("Winter 2027", reloaded.getTerm());
        assertEquals(3, reloaded.getAnalysis().orElseThrow().keywords().size());
    }

    @Test
    void unknownPostingIdIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> facade.analyzePosting("nope"));
    }

}
