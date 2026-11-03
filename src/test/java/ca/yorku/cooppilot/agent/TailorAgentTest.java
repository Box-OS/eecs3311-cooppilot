package ca.yorku.cooppilot.agent;

import ca.yorku.cooppilot.agent.tools.ToolCall;
import ca.yorku.cooppilot.agent.tools.ToolResult;
import ca.yorku.cooppilot.app.CoopPilotApplication;
import ca.yorku.cooppilot.app.CoopPilotFacade;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.llm.LLMClient;
import ca.yorku.cooppilot.llm.LlmResponse;
import ca.yorku.cooppilot.llm.MockLlmClient;
import ca.yorku.cooppilot.llm.PostingAnalysisTaskTest;
import ca.yorku.cooppilot.persistence.ResumeRepository;
import ca.yorku.cooppilot.persistence.VaultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** F05 end to end: scripted LLM decisions, real tools, real services and SQLite. */
@SpringBootTest(classes = CoopPilotApplication.class)
class TailorAgentTest {

    @Autowired CoopPilotFacade facade;
    @Autowired LLMClient llm;
    @Autowired ResumeRepository resumeRepo;
    @Autowired VaultRepository vaultRepo;

    private final ObjectMapper json = new ObjectMapper();
    private final List<ToolResult> results = new ArrayList<>();

    private LlmResponse call(String id, String name, String args) throws Exception {
        return new LlmResponse("", List.of(new ToolCall(id, name, json.readTree(args))), "tool_use", 0, 0);
    }

    @Test
    void agentTailorsSavesAndTheRulesHold() throws Exception {
        vaultRepo.findAll().forEach(e -> vaultRepo.delete(e.getId()));
        resumeRepo.findAll().forEach(r -> resumeRepo.delete(r.getId()));
        Resume base = facade.importResume(Files.readString(Path.of("samples/sample-resume.tex")), "CV_General").resume();
        MockLlmClient mock = (MockLlmClient) llm;
        mock.enqueueText(PostingAnalysisTaskTest.GOOD);
        String postingId = facade.analyzePosting(facade.importPosting("Northwind posting", "", "").getId()).getId();

        String tutor = id(base, "Peer Tutor"), cashier = id(base, "Cashier");
        String study = id(base, "StudyBuddy"), maze = id(base, "Maze Solver");
        mock.enqueue(call("1", "find_similar_resume", "{}"))
            .enqueue(call("2", "search_vault", "{\"query\":\"SQL\"}"))
            .enqueue(call("3", "compose_resume", """
                {"base_resume_id":"%s","project_order":["%s","%s"],"skills_first":["SQL","Java"],
                 "replace_bullets":[
                   {"block_id":"%s","index":0,"text":"Tutored \\\\textbf{20+} first-year students weekly in \\\\textbf{Java} and SQL basics"},
                   {"block_id":"%s","index":0,"text":"Handled 500 transactions per shift"}]}"""
                .formatted(base.getId(), maze, study, tutor, cashier)))
            .enqueue(call("4", "keyword_scan", "{}"))
            .enqueue(call("5", "save_resume", "{\"family\":\"backend\"}"))
            .enqueueText("Saved the tailored resume.");

        AgentResult r = facade.tailorResume(postingId, QuestionHandler.NONE, new AgentEventListener() {
            public void onToolCalled(ToolCall c) {}
            public void onToolResult(ToolResult res) { results.add(res); }
            public void onFinished(AgentResult res) {}
        });

        assertEquals(AgentResult.Status.COMPLETED, r.status(), r.summary());
        Resume saved = r.resume();
        assertEquals("CV_NorthwindLabs_SoftwareDeveloper", saved.getName());
        assertEquals("backend", saved.getFamily());
        assertTrue(resumeRepo.findByName(saved.getName()).isPresent());
        assertTrue(results.stream().allMatch(ToolResult::ok), () -> results.toString());

        // the honest rewrite was applied, the invented number was rejected
        String compose = results.get(2).content();
        assertTrue(compose.contains("REJECTED") && compose.contains("500"), compose);
        assertTrue(block(saved, "Peer Tutor").getBullets().get(0).contains("SQL basics"));
        assertTrue(block(saved, "Cashier").getBullets().get(0).contains("150+"));

        String tex = saved.getLatex();
        assertTrue(tex.indexOf("Peer Tutor") < tex.indexOf("Cashier"), "reverse-chronological experience");
        assertTrue(tex.indexOf("Maze Solver") < tex.indexOf("StudyBuddy"), "projects in the agent's order");
        assertTrue(tex.contains("\\textbf{Languages}{: SQL, Java, Python"), "skills front-loaded, label stays bold");
        String education = tex.substring(tex.indexOf("\\section{Education}"), tex.indexOf("\\section{Experience}"));
        assertFalse(education.contains("\\textbf"), "no bold in Education");
        assertTrue(tex.startsWith("%-------------------------") || tex.contains("\\documentclass"));
        assertTrue(tex.contains("Alex Chen"), "contact header kept from the base resume");
        assertTrue(results.get(3).content().contains("coverage"));
    }

    private static String id(Resume r, String title) {
        return block(r, title).getId();
    }

    private static ResumeBlock block(Resume r, String title) {
        return r.getBlocks().stream().filter(b -> b.getTitle().equals(title)).findFirst().orElseThrow();
    }

}
