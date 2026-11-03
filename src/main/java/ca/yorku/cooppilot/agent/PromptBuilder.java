package ca.yorku.cooppilot.agent;

import ca.yorku.cooppilot.agent.tools.ToolSchema;
import ca.yorku.cooppilot.domain.Posting;
import ca.yorku.cooppilot.llm.LlmRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/** Builds the agent's system prompt (goal + hard rules) and each step's request. */
@Component
public class PromptBuilder {

    static final List<String> RULES = List.of(
            "Use only facts from the experience vault or saved resumes. Never invent employers, dates, titles, tools or numbers.",
            "If a number is unknown, write a placeholder with an estimate like [N=5] or [X=30\\%]; if a non-numeric fact is missing, call ask_user.",
            "Keep Experience and Leadership in reverse-chronological order; Projects may be ordered by relevance.",
            "Never bold words in Education or Technical Skills.",
            "The resume must fit on one page: call estimate_page after composing and condense if it is over.",
            "Bullets: strong verb + what was done + result or scale, about 15-25 words, one idea each; never label soft skills.",
            "Save the final resume with save_resume (named CV_Company_Role), then reply with a short summary.");

    public String buildSystemPrompt(AgentGoal goal) {
        Posting p = goal.posting();
        StringBuilder sb = new StringBuilder();
        sb.append("You are CoopPilot, an agent that tailors a co-op student's one-page LaTeX resume to a job posting.\n")
          .append("Work step by step: look at what already exists, decide, call one tool at a time, check the result.\n\n")
          .append("GOAL: ").append(goal.type() == AgentGoal.GoalType.TAILOR_RESUME
                        ? "Produce a tailored resume for this posting." : "Fit the current resume to one page.")
          .append("\nPOSTING: ").append(p.getCompany()).append(" — ").append(p.getRole())
          .append(" (id ").append(p.getId()).append(")\n\nRULES:\n");
        for (int i = 0; i < RULES.size(); i++) sb.append(i + 1).append(". ").append(RULES.get(i)).append('\n');
        for (String c : goal.constraints()) sb.append("- ").append(c).append('\n');
        return sb.toString();
    }

    public LlmRequest buildRequest(AgentSession s, List<ToolSchema> schemas, int maxTokens) {
        return new LlmRequest(buildSystemPrompt(s.goal()), s.messages(), schemas, maxTokens);
    }

    public String firstUserMessage(AgentGoal goal) {
        return "Tailor my resume for posting " + goal.posting().getId() + ". Start by checking which saved resume "
                + "is closest and what the posting needs.";
    }
}
