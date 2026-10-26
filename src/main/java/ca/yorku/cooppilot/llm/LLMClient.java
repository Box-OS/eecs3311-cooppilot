package ca.yorku.cooppilot.llm;

/**
 * Adapter pattern — Target. The agent and the LLM tasks only know this interface, so the provider
 * (Claude, an OpenAI-compatible endpoint, or the offline mock) can change without touching them.
 */
public interface LLMClient {

    LlmResponse complete(LlmRequest request);

    String modelName();
}
