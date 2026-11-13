package ca.yorku.cooppilot.ui.web;

import ca.yorku.cooppilot.agent.tools.ToolCall;
import ca.yorku.cooppilot.app.CoopPilotApplication;
import ca.yorku.cooppilot.app.CoopPilotFacade;
import ca.yorku.cooppilot.llm.LLMClient;
import ca.yorku.cooppilot.llm.LlmResponse;
import ca.yorku.cooppilot.llm.MockLlmClient;
import ca.yorku.cooppilot.llm.PostingAnalysisTaskTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The web agent run: background thread, buffered events, question answered from the "browser". */
@SpringBootTest(classes = CoopPilotApplication.class)
class AgentSessionManagerTest {

    @Autowired CoopPilotFacade facade;
    @Autowired LLMClient llm;

    @Test
    void questionIsStreamedAndTheAnswerReachesTheAgent() throws Exception {
        MockLlmClient mock = (MockLlmClient) llm;
        mock.enqueueText(PostingAnalysisTaskTest.GOOD);
        String postingId = facade.analyzePosting(facade.importPosting("Northwind posting", "", "").getId()).getId();
        mock.enqueue(new LlmResponse("I need one detail.", List.of(new ToolCall("q1", "ask_user",
                        new ObjectMapper().readTree("{\"question\":\"Which framework did the Maze Solver visualizer use?\",\"topic\":\"Maze Solver\"}"))),
                        "tool_use", 0, 0))
            .enqueueText("Stopping here for the test.");

        AgentSessionManager agents = new AgentSessionManager(facade, 10);
        String runId = agents.start(postingId);

        long deadline = System.currentTimeMillis() + 10_000;
        while (agents.events(runId).stream().noneMatch(e -> e.type().equals("question"))) {
            assertTrue(System.currentTimeMillis() < deadline, "question event never arrived");
            Thread.sleep(20);
        }
        assertTrue(agents.answer(runId, "SDL2 rendering"));

        while (agents.result(runId).isEmpty()) {
            assertTrue(System.currentTimeMillis() < deadline, "run never finished");
            Thread.sleep(20);
        }
        List<String> types = agents.events(runId).stream().map(AgentSessionManager.Event::type).toList();
        assertEquals(List.of("thinking", "tool", "question", "result", "thinking", "thinking", "thinking", "done"), types,
                "the model stops without saving, so it is nudged twice before the run ends");
        String result = String.valueOf(agents.events(runId).get(3).data().get("text"));
        assertTrue(result.contains("SDL2 rendering"), result);
        assertFalse(agents.answer(runId, "late"), "no question pending any more");
        assertNotNull(agents.subscribe(runId), "late subscribers get the buffered events");
    }

    @Test
    void unknownPostingFailsFast() {
        AgentSessionManager agents = new AgentSessionManager(facade, 1);
        assertThrows(IllegalArgumentException.class, () -> agents.start("nope"));
    }
}
