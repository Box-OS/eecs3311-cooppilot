package ca.yorku.cooppilot.app;

import ca.yorku.cooppilot.agent.AgentController;
import ca.yorku.cooppilot.agent.PromptBuilder;
import ca.yorku.cooppilot.agent.tools.Tool;
import ca.yorku.cooppilot.agent.tools.ToolRegistry;
import ca.yorku.cooppilot.llm.ClaudeAdapter;
import ca.yorku.cooppilot.llm.LLMClient;
import ca.yorku.cooppilot.llm.MockLlmClient;
import ca.yorku.cooppilot.service.CondenseStrategy;
import ca.yorku.cooppilot.service.LlmCondense;
import ca.yorku.cooppilot.service.RuleBasedCondense;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Wires the concrete classes in one place (dependency inversion): everything else depends on interfaces. */
@Configuration
public class AppConfig {

    @Bean
    public LLMClient llmClient(@Value("${cooppilot.llm.provider:mock}") String provider,
                               @Value("${cooppilot.llm.api-key:}") String apiKey,
                               @Value("${cooppilot.llm.model:claude-haiku-4-5}") String model) {
        return switch (provider.toLowerCase()) {
            case "claude" -> new ClaudeAdapter(apiKey, model);
            case "mock" -> new MockLlmClient();
            default -> throw new IllegalStateException("Unknown cooppilot.llm.provider: " + provider);
        };
    }

    @Bean
    public ToolRegistry toolRegistry(List<Tool> tools) {
        return new ToolRegistry(tools);
    }

    @Bean
    public AgentController agentController(LLMClient llm, ToolRegistry tools, PromptBuilder prompts,
                                           ca.yorku.cooppilot.service.ResumeLibrary library,
                                           ca.yorku.cooppilot.service.ExportService export,
                                           @Value("${cooppilot.agent.max-steps:15}") int maxSteps,
                                           @Value("${cooppilot.llm.max-tokens:2048}") int maxTokens) {
        AgentController agent = new AgentController(llm, tools, prompts, maxSteps, maxTokens);
        agent.setDraftSaver((draft, posting) -> {
            String name = export.fileName("CV", posting.getCompany(), posting.getRole()) + "_Draft";
            String id = library.byName(name).map(ca.yorku.cooppilot.domain.Resume::getId).orElse(null);
            var saved = new ca.yorku.cooppilot.domain.Resume(id, name, draft.getFamily(),
                    ca.yorku.cooppilot.domain.Resume.Kind.GENERATED, draft.getBlocks(), draft.getLatex());
            library.save(saved);
            return saved;
        });
        return agent;
    }

    /** Strategy pattern: the condense algorithms selectable by mode. */
    @Bean
    public Map<CondenseStrategy.Mode, CondenseStrategy> condenseStrategies(RuleBasedCondense rules, LlmCondense smart) {
        Map<CondenseStrategy.Mode, CondenseStrategy> m = new EnumMap<>(CondenseStrategy.Mode.class);
        m.put(CondenseStrategy.Mode.RULES, rules);
        m.put(CondenseStrategy.Mode.SMART, smart);
        return m;
    }

    /** Injected so date-based rules (follow-ups, streaks) can be tested with a fixed clock. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
