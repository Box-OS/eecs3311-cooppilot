package ca.yorku.cooppilot.agent;

import ca.yorku.cooppilot.agent.tools.ToolCall;
import ca.yorku.cooppilot.agent.tools.ToolRegistry;
import ca.yorku.cooppilot.agent.tools.ToolResult;
import ca.yorku.cooppilot.llm.LLMClient;
import ca.yorku.cooppilot.llm.LlmException;
import ca.yorku.cooppilot.llm.LlmResponse;

/**
 * F05: the reason–act–observe loop. Each step asks the LLM what to do next; every tool call it makes
 * is validated and run by the {@link ToolRegistry}; results go back into the conversation. The loop
 * ends when the model stops calling tools, or at {@code maxSteps}.
 */
public class AgentController {

    private final LLMClient llm;
    private final ToolRegistry tools;
    private final PromptBuilder prompts;
    private final int maxSteps;
    private final int maxTokens;

    public AgentController(LLMClient llm, ToolRegistry tools, PromptBuilder prompts, int maxSteps, int maxTokens) {
        this.llm = llm;
        this.tools = tools;
        this.prompts = prompts;
        this.maxSteps = maxSteps;
        this.maxTokens = maxTokens;
    }

    public AgentResult run(AgentGoal goal, QuestionHandler questions, AgentEventListener listener) {
        AgentSession s = new AgentSession(goal, questions);
        s.addListener(listener);
        s.addMessage(Message.user(prompts.firstUserMessage(goal)));
        return loop(s);
    }

    AgentResult loop(AgentSession s) {
        int in = 0, out = 0;
        String lastText = "";
        while (s.stepCount() < maxSteps) {
            s.nextStep();
            LlmResponse r;
            try {
                r = llm.complete(prompts.buildRequest(s, tools.schemas(), maxTokens));
            } catch (LlmException e) {
                return finish(s, AgentResult.Status.FAILED, "LLM error: " + e.getMessage(), in, out);
            }
            in += r.inputTokens();
            out += r.outputTokens();
            lastText = r.text();
            s.addMessage(ca.yorku.cooppilot.agent.Message.assistant(r.text(), r.toolCalls()));
            s.notifyThinking(r.text());

            if (!r.hasToolCalls()) {
                AgentResult.Status st = s.saved().isPresent() ? AgentResult.Status.COMPLETED : AgentResult.Status.FAILED;
                String summary = st == AgentResult.Status.COMPLETED ? r.text()
                        : (r.text().isBlank() ? "The agent stopped without saving a resume." : r.text());
                return finish(s, st, summary, in, out);
            }
            for (ToolCall call : r.toolCalls()) {
                s.notifyToolCalled(call);
                ToolResult result = tools.execute(call, s);
                s.notifyToolResult(result);
                s.addMessage(Message.toolResult(call.id(), result.forModel(), !result.ok()));
            }
        }
        return finish(s, AgentResult.Status.STEP_LIMIT,
                "Stopped after " + maxSteps + " steps." + (lastText.isBlank() ? "" : " Last note: " + lastText), in, out);
    }

    private static AgentResult finish(AgentSession s, AgentResult.Status st, String summary, int in, int out) {
        AgentResult result = new AgentResult(st, s.saved().or(s::draft).orElse(null), summary, s.stepCount(), in, out);
        s.notifyFinished(result);
        return result;
    }
}
