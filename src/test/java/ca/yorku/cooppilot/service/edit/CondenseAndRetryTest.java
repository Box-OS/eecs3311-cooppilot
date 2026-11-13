package ca.yorku.cooppilot.service.edit;

import ca.yorku.cooppilot.agent.AgentController;
import ca.yorku.cooppilot.agent.AgentGoal;
import ca.yorku.cooppilot.agent.AgentResult;
import ca.yorku.cooppilot.agent.PromptBuilder;
import ca.yorku.cooppilot.agent.QuestionHandler;
import ca.yorku.cooppilot.agent.tools.ToolRegistry;
import ca.yorku.cooppilot.app.CoopPilotApplication;
import ca.yorku.cooppilot.app.CoopPilotFacade;
import ca.yorku.cooppilot.domain.Posting;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.Section;
import ca.yorku.cooppilot.llm.LLMClient;
import ca.yorku.cooppilot.llm.LlmException;
import ca.yorku.cooppilot.llm.LlmRequest;
import ca.yorku.cooppilot.llm.LlmResponse;
import ca.yorku.cooppilot.llm.MockLlmClient;
import ca.yorku.cooppilot.llm.PostingAnalysisTaskTest;
import ca.yorku.cooppilot.persistence.ResumeRepository;
import ca.yorku.cooppilot.persistence.VaultRepository;
import ca.yorku.cooppilot.service.CondenseStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** F07 from the GUI/CLI as an undoable command (page size forced small), and the agent's LLM retry. */
@SpringBootTest(classes = CoopPilotApplication.class, properties = "cooppilot.page.lines-per-page=26")
class CondenseAndRetryTest {

    @Autowired CoopPilotFacade facade;
    @Autowired LLMClient llm;
    @Autowired ResumeRepository resumeRepo;
    @Autowired VaultRepository vaultRepo;

    @Test
    void condenseFitsTheResumeAndCanBeUndone() throws Exception {
        vaultRepo.findAll().forEach(e -> vaultRepo.delete(e.getId()));
        resumeRepo.findAll().forEach(r -> resumeRepo.delete(r.getId()));
        Resume r = facade.importResume(Files.readString(Path.of("samples/sample-resume.tex")), "CV_Long").resume();
        ((MockLlmClient) llm).enqueueText(PostingAnalysisTaskTest.GOOD);
        String postingId = facade.analyzePosting(facade.importPosting("Northwind posting", "", "").getId()).getId();
        double before = facade.pageEstimate(r.getId());
        assertTrue(before > 1.0, "precondition: over one page with 26 lines per page, was " + before);
        int bulletsBefore = bullets(facade.resume(r.getId()));

        facade.condenseResume(r.getId(), postingId, CondenseStrategy.Mode.RULES);
        assertTrue(facade.pageEstimate(r.getId()) <= 1.0);
        assertTrue(bullets(facade.resume(r.getId())) < bulletsBefore);
        assertTrue(facade.revisionLog(r.getId()).get(0).startsWith("Condensed"));
        assertFalse(facade.resume(r.getId()).blocksIn(Section.EXPERIENCE).isEmpty(), "jobs kept");

        facade.undo(r.getId());
        assertEquals(bulletsBefore, bullets(facade.resume(r.getId())));
        assertEquals(before, facade.pageEstimate(r.getId()), 1e-9);
    }

    private static int bullets(Resume r) {
        return r.getBlocks().stream().mapToInt(b -> b.getBullets().size()).sum();
    }

    private final AgentGoal goal = new AgentGoal(AgentGoal.GoalType.TAILOR_RESUME,
            new Posting("p", "N", "Dev", "t", "", null), List.of());

    @Test
    void transientLlmErrorsAreRetriedThenTheRunContinues() {
        AtomicInteger calls = new AtomicInteger();
        LLMClient flaky = new LLMClient() {
            public LlmResponse complete(LlmRequest req) {
                if (calls.incrementAndGet() <= 2) throw new LlmException("overloaded", 529);
                return new LlmResponse("Nothing to do.", List.of(), "end_turn", 0, 0);
            }
            public String modelName() { return "flaky"; }
        };
        AgentResult r = new AgentController(flaky, new ToolRegistry(new ArrayList<>()), new PromptBuilder(), 5, 100, 2, 1)
                .run(goal, QuestionHandler.NONE, null);
        assertEquals(3 + AgentController.MAX_NUDGES, calls.get(), "two retries, then success, then the nudges");
        assertEquals(AgentResult.Status.FAILED, r.status(), "ended without saving, but not because of the LLM");
        assertFalse(r.summary().contains("LLM error"));
    }

    @Test
    void nonRetryableErrorsFailAtOnce() {
        AtomicInteger calls = new AtomicInteger();
        LLMClient bad = new LLMClient() {
            public LlmResponse complete(LlmRequest req) { calls.incrementAndGet(); throw new LlmException("invalid x-api-key", 401); }
            public String modelName() { return "bad"; }
        };
        AgentResult r = new AgentController(bad, new ToolRegistry(new ArrayList<>()), new PromptBuilder(), 5, 100, 2, 1)
                .run(goal, QuestionHandler.NONE, null);
        assertEquals(1, calls.get());
        assertTrue(r.summary().contains("invalid x-api-key"));
    }

}
