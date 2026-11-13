package ca.yorku.cooppilot.agent;

import ca.yorku.cooppilot.agent.tools.Schemas;
import ca.yorku.cooppilot.agent.tools.Tool;
import ca.yorku.cooppilot.agent.tools.ToolCall;
import ca.yorku.cooppilot.agent.tools.ToolRegistry;
import ca.yorku.cooppilot.agent.tools.ToolResult;
import ca.yorku.cooppilot.domain.Posting;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.llm.LLMClient;
import ca.yorku.cooppilot.llm.LlmException;
import ca.yorku.cooppilot.llm.LlmRequest;
import ca.yorku.cooppilot.llm.LlmResponse;
import ca.yorku.cooppilot.llm.MockLlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentControllerTest {

    static final ObjectMapper JSON = new ObjectMapper();

    /** Echoes its "text" argument. */
    static class EchoTool implements Tool {
        public String name() { return "echo"; }
        public String description() { return "echo text"; }
        public JsonNode inputSchema() { return Schemas.object().prop("text", "string", "what to echo", true).build(); }
        public ToolResult execute(JsonNode args, AgentSession s) { return ToolResult.ok("echo: " + args.get("text").asText()); }
    }

    /** Marks a resume as saved. */
    static class SaveTool implements Tool {
        public String name() { return "save_resume"; }
        public String description() { return "save"; }
        public JsonNode inputSchema() { return Schemas.object().prop("name", "string", "file name", true).build(); }
        public ToolResult execute(JsonNode args, AgentSession s) {
            s.markSaved(new Resume(null, args.get("name").asText(), "", Resume.Kind.TAILORED, List.of(), ""));
            return ToolResult.ok("saved");
        }
    }

    /** Records every event it sees. */
    static class Recorder implements AgentEventListener {
        final List<String> events = new ArrayList<>();
        public void onToolCalled(ToolCall c) { events.add("call:" + c.name()); }
        public void onToolResult(ToolResult r) { events.add((r.ok() ? "ok:" : "err:") + r.toolName()); }
        public void onFinished(AgentResult r) { events.add("done:" + r.status()); }
    }

    static LlmResponse calls(ToolCall... c) {
        return new LlmResponse("", List.of(c), "tool_use", 10, 5);
    }

    static ToolCall call(String id, String name, String argsJson) throws Exception {
        return new ToolCall(id, name, JSON.readTree(argsJson));
    }

    private final AgentGoal goal = new AgentGoal(AgentGoal.GoalType.TAILOR_RESUME,
            new Posting("p1", "Northwind", "Dev Co-op", "text", "", null), List.of());

    private AgentController controller(LLMClient llm, int maxSteps) {
        return new AgentController(llm, new ToolRegistry(List.of(new EchoTool(), new SaveTool())),
                new PromptBuilder(), maxSteps, 500);
    }

    @Test
    void runsToolsUntilTheModelStopsAndNotifiesListeners() throws Exception {
        MockLlmClient llm = new MockLlmClient()
                .enqueue(calls(call("1", "echo", "{\"text\":\"hi\"}")))
                .enqueue(calls(call("2", "save_resume", "{\"name\":\"CV_Northwind_DevCoop\"}")))
                .enqueueText("Saved CV_Northwind_DevCoop.");
        Recorder rec = new Recorder();

        AgentResult r = controller(llm, 15).run(goal, QuestionHandler.NONE, rec);

        assertEquals(AgentResult.Status.COMPLETED, r.status());
        assertEquals("CV_Northwind_DevCoop", r.resume().getName());
        assertEquals(3, r.steps());
        assertEquals(20, r.inputTokens(), "token usage is summed over steps");
        assertEquals(List.of("call:echo", "ok:echo", "call:save_resume", "ok:save_resume", "done:COMPLETED"), rec.events);

        // the second request carries the first tool's result back to the model
        LlmRequest second = llm.received().get(1);
        assertTrue(second.messages().stream().anyMatch(m -> m.role() == Message.Role.TOOL && m.content().equals("echo: hi")));
        assertTrue(second.system().contains("one page"), "rules are in the system prompt");
        assertEquals(2, second.tools().size());
    }

    @Test
    void invalidArgumentsAndUnknownToolsGoBackToTheModelAsErrors() throws Exception {
        MockLlmClient llm = new MockLlmClient()
                .enqueue(calls(call("1", "echo", "{}"), call("2", "delete_everything", "{}"),
                        call("3", "echo", "{\"text\":5}")))
                .enqueueText("I could not do it.");
        Recorder rec = new Recorder();

        AgentResult r = controller(llm, 15).run(goal, QuestionHandler.NONE, rec);

        assertEquals(AgentResult.Status.FAILED, r.status(), "ended without saving");
        List<Message> toolMsgs = llm.received().get(1).messages().stream()
                .filter(m -> m.role() == Message.Role.TOOL).toList();
        assertEquals(3, toolMsgs.size());
        assertTrue(toolMsgs.get(0).error() && toolMsgs.get(0).content().contains("missing 'text'"));
        assertTrue(toolMsgs.get(1).content().contains("unknown tool"));
        assertTrue(toolMsgs.get(2).content().contains("'text' must be string"));
    }

    @Test
    void modelThatStopsToChatIsNudgedToFinishAndSave() throws Exception {
        // what happened on the first real run: the model asked "Would you like me to...?" instead of saving
        MockLlmClient llm = new MockLlmClient()
                .enqueue(calls(call("1", "echo", "{\"text\":\"draft\"}")))
                .enqueueText("Would you like me to refine the draft or search your vault?")
                .enqueue(calls(call("2", "save_resume", "{\"name\":\"CV_X\"}")))
                .enqueueText("Saved.");
        AgentResult r = controller(llm, 15).run(goal, QuestionHandler.NONE, new Recorder());

        assertEquals(AgentResult.Status.COMPLETED, r.status());
        LlmRequest afterChat = llm.received().get(2);
        Message nudge = afterChat.messages().get(afterChat.messages().size() - 1);
        assertEquals(Message.Role.USER, nudge.role());
        assertTrue(nudge.content().contains("save_resume"));
        assertTrue(afterChat.system().contains("autonomously"));
    }

    @Test
    void nudgesAreLimitedSoAChattyModelStillEnds() {
        MockLlmClient llm = new MockLlmClient().enqueueText("Shall I?").enqueueText("Shall I?").enqueueText("Shall I?");
        AgentResult r = controller(llm, 15).run(goal, QuestionHandler.NONE, new Recorder());
        assertEquals(AgentResult.Status.FAILED, r.status());
        assertEquals(1 + AgentController.MAX_NUDGES, llm.received().size());
    }

    /** Sets a draft (like compose_resume does). */
    static class DraftTool implements Tool {
        public String name() { return "compose_resume"; }
        public String description() { return "draft"; }
        public JsonNode inputSchema() { return Schemas.object().build(); }
        public ToolResult execute(JsonNode args, AgentSession s) {
            s.updateDraft(new Resume(null, "draft", "", Resume.Kind.TAILORED, List.of(), "\\documentclass"));
            return ToolResult.ok("drafted");
        }
    }

    @Test
    void partialDraftIsSavedWhenTheStepLimitIsReached() throws Exception {
        MockLlmClient llm = new MockLlmClient();
        for (int i = 0; i < 5; i++) llm.enqueue(calls(call("d" + i, "compose_resume", "{}")));
        AgentController c = new AgentController(llm, new ToolRegistry(List.of(new DraftTool())), new PromptBuilder(), 3, 500);
        List<String> savedNames = new ArrayList<>();
        c.setDraftSaver((draft, posting) -> {
            Resume r = new Resume(null, "CV_" + posting.getCompany() + "_Draft", "", Resume.Kind.GENERATED, draft.getBlocks(), draft.getLatex());
            savedNames.add(r.getName());
            return r;
        });
        AgentResult r = c.run(goal, QuestionHandler.NONE, new Recorder());
        assertEquals(AgentResult.Status.STEP_LIMIT, r.status());
        assertTrue(r.resumeSaved());
        assertEquals(List.of("CV_Northwind_Draft"), savedNames);
        assertTrue(r.summary().contains("partial draft was saved as CV_Northwind_Draft"));
    }

    @Test
    void noDraftNoSave() throws Exception {
        MockLlmClient llm = new MockLlmClient();
        for (int i = 0; i < 5; i++) llm.enqueue(calls(call("e" + i, "echo", "{\"text\":\"x\"}")));
        AgentController c = controller(llm, 2);
        c.setDraftSaver((d, p) -> { throw new AssertionError("nothing to save"); });
        AgentResult r = c.run(goal, QuestionHandler.NONE, new Recorder());
        assertFalse(r.resumeSaved());
    }

    @Test
    void stopsAtTheStepLimit() throws Exception {
        MockLlmClient llm = new MockLlmClient();
        for (int i = 0; i < 10; i++) llm.enqueue(calls(call("c" + i, "echo", "{\"text\":\"again\"}")));
        AgentResult r = controller(llm, 4).run(goal, QuestionHandler.NONE, new Recorder());
        assertEquals(AgentResult.Status.STEP_LIMIT, r.status());
        assertEquals(4, r.steps());
        assertEquals(4, llm.received().size());
    }

    @Test
    void llmFailureEndsTheRunCleanly() {
        LLMClient broken = new LLMClient() {
            public LlmResponse complete(LlmRequest req) { throw new LlmException("overloaded", 529); }
            public String modelName() { return "broken"; }
        };
        Recorder rec = new Recorder();
        AgentResult r = controller(broken, 15).run(goal, QuestionHandler.NONE, rec);
        assertEquals(AgentResult.Status.FAILED, r.status());
        assertTrue(r.summary().contains("overloaded"));
        assertEquals(List.of("done:FAILED"), rec.events);
    }
}
