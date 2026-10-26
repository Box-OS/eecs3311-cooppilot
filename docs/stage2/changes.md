# Stage 2 — Changes from the Stage 1 design

Running log, one entry per change: Original design → Implemented change → Reason → Impact.

## C01 — ClaudeAdapter talks to the Messages API over HTTP instead of the Anthropic Java SDK
- **Original:** `ClaudeAdapter` wraps the external `AnthropicClient` SDK (`toProvider(...) : MessageCreateParams`).
- **Implemented:** `ClaudeAdapter` builds the Messages API JSON itself (Jackson) and sends it with `java.net.http.HttpClient`. `toProvider` returns JSON; `fromProvider` parses JSON.
- **Reason:** one less dependency to version-match; the translation is small and fully unit-testable without network access (`ClaudeAdapterTest`).
- **Impact:** Adapter pattern unchanged (`LLMClient` is still the Target, `ClaudeAdapter` the Adapter); `AnthropicClient` disappears from class diagram 1.

## C02 — Message carries tool calls and tool results
- **Original:** `Message` had `role` and `content` only.
- **Implemented:** `Message(role, content, toolCalls, toolCallId, error)` with roles USER / ASSISTANT / TOOL.
- **Reason:** the reason–act–observe loop must send the model's tool calls and each tool's result back on the next step.
- **Impact:** `AgentSession.messages` and `LlmRequest` now hold the full tool conversation; diagram 1 attribute list updated.
