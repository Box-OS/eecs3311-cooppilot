package ca.yorku.cooppilot.app;

import ca.yorku.cooppilot.llm.ClaudeAdapter;
import ca.yorku.cooppilot.llm.LLMClient;
import ca.yorku.cooppilot.llm.MockLlmClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
