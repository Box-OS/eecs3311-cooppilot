package ca.yorku.cooppilot.llm;

import ca.yorku.cooppilot.agent.Message;
import ca.yorku.cooppilot.agent.tools.ToolCall;
import ca.yorku.cooppilot.agent.tools.ToolSchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Adapter pattern — a second Adapter. Runs each request through the Claude Code CLI ({@code claude -p}) signed in with a
 * Claude subscription, so the app works without an API key. The CLI has no tool-use protocol of its own here (its built-in
 * tools are switched off), so the conversation and the tool list are written into the prompt, and the reply is
 * structured output checked against a JSON Schema: {@code {"text": ..., "tool_calls": [{"name", "args"}]}}.
 */
public class ClaudeCliAdapter implements LLMClient {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ClaudeCliAdapter.class);

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long TIMEOUT_SECONDS = 240;

    /** Starts the CLI process; replaced in tests. */
    interface Runner {
        String run(List<String> command, Map<String, String> env, String stdin) throws IOException, InterruptedException;
    }

    private final String command;
    private final String model;
    private final String configDir;
    private final String tokenFile;
    private final Runner runner;

    /**
     * @param command   the CLI to run (usually {@code claude})
     * @param configDir optional CLAUDE_CONFIG_DIR: which signed-in account to use (blank: the CLI's usual one)
     * @param tokenFile optional file holding a token from {@code claude setup-token}, passed as CLAUDE_CODE_OAUTH_TOKEN
     */
    public ClaudeCliAdapter(String command, String model, String configDir, String tokenFile) {
        this(command, model, configDir, tokenFile, ClaudeCliAdapter::runProcess);
    }

    ClaudeCliAdapter(String command, String model, String configDir, String tokenFile, Runner runner) {
        this.command = command == null || command.isBlank() ? "claude" : command;
        this.model = model;
        this.configDir = configDir == null ? "" : configDir;
        this.tokenFile = tokenFile == null ? "" : tokenFile;
        this.runner = runner;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        Path systemFile = null;
        try {
            systemFile = Files.createTempFile("cooppilot-system", ".txt");
            Files.writeString(systemFile, system(request), StandardCharsets.UTF_8);
            List<String> cmd = new ArrayList<>(List.of(command, "-p", "--model", model, "--output-format", "json",
                    "--tools", "", "--strict-mcp-config", "--setting-sources", "", "--disable-slash-commands",
                    "--no-session-persistence",
                    "--system-prompt-file", systemFile.toString()));
            long t0 = System.currentTimeMillis();
            String out = runner.run(cmd, env(), transcript(request.messages()));
            LlmResponse r = parse(out, !request.tools().isEmpty());
            LOG.info("claude -p {}: {} ms, {} in / {} out tokens{}", model, System.currentTimeMillis() - t0,
                    r.inputTokens(), r.outputTokens(), r.hasToolCalls() ? ", calls " + r.toolCalls().size() : "");
            return r;
        } catch (IOException e) {
            throw new LlmException("Could not run the Claude CLI (" + command + "): " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while waiting for the Claude CLI", e);
        } finally {
            if (systemFile != null) {
                try { Files.deleteIfExists(systemFile); } catch (IOException ignored) { /* temp file */ }
            }
        }
    }

    @Override
    public String modelName() {
        return model;
    }

    /** The request's system prompt, plus the tools and how to call them when there are any. */
    String system(LlmRequest req) {
        StringBuilder s = new StringBuilder(req.system() == null ? "" : req.system());
        if (!req.tools().isEmpty()) {
            s.append("\n\n# Tools\nThe tools below are available and working. CoopPilot runs them for you: to call them,")
             .append(" reply with ONLY one JSON object, no prose or code fences around it:\n")
             .append("{\"text\": \"a short note for the user\", \"tool_calls\": [{\"name\": \"tool_name\", \"args\": {...}}]}\n")
             .append("args must match the tool's input schema. The results come back in the next turn as [tool result #id].")
             .append(" You may call several tools in one reply. When the task is finished, reply with \"tool_calls\": [].\n");
            for (ToolSchema t : req.tools()) {
                s.append("\n## ").append(t.name()).append('\n').append(t.description())
                 .append("\nInput schema: ").append(t.inputSchema()).append('\n');
            }
        }
        return s.toString();
    }

    /** The conversation so far as plain text, oldest first. */
    static String transcript(List<Message> messages) {
        if (messages.size() == 1 && messages.get(0).role() == Message.Role.USER) return messages.get(0).content();
        StringBuilder s = new StringBuilder("The conversation so far:\n");
        for (Message m : messages) {
            switch (m.role()) {
                case USER -> s.append("\n[user]\n").append(m.content()).append('\n');
                case ASSISTANT -> {
                    s.append("\n[you]\n");
                    if (!m.content().isBlank()) s.append(m.content()).append('\n');
                    for (ToolCall c : m.toolCalls()) {
                        s.append("(called ").append(c.name()).append(" #").append(c.id()).append(" with ").append(c.args()).append(")\n");
                    }
                }
                case TOOL -> s.append("\n[tool result #").append(m.toolCallId()).append(m.error() ? ", error" : "").append("]\n")
                        .append(m.content()).append('\n');
            }
        }
        return s.append("\nContinue from here: reply as [you].").toString();
    }

    /** The CLI's JSON result -> neutral response. */
    LlmResponse parse(String out, boolean withTools) {
        JsonNode root;
        try {
            root = JSON.readTree(out);
        } catch (IOException e) {
            throw new LlmException("The Claude CLI returned something that isn't JSON: " + abbreviate(out), e);
        }
        if (root == null || root.isMissingNode() || root.path("is_error").asBoolean(false)) {
            String why = root == null ? out : root.path("result").asText("unknown error");
            throw new LlmException("Claude CLI error: " + why, 0);
        }
        JsonNode usage = root.path("usage");
        int in = usage.path("input_tokens").asInt(0) + usage.path("cache_read_input_tokens").asInt(0)
                + usage.path("cache_creation_input_tokens").asInt(0);
        int outTokens = usage.path("output_tokens").asInt(0);
        if (!withTools) return new LlmResponse(root.path("result").asText(""), List.of(), "end_turn", in, outTokens);

        String result = root.path("result").asText("");
        JsonNode reply;
        try {
            reply = JsonReply.object(result);
        } catch (IllegalArgumentException e) { // plain prose: a final answer with no tool calls
            return new LlmResponse(result, List.of(), "end_turn", in, outTokens);
        }
        List<ToolCall> calls = new ArrayList<>();
        int n = 0;
        for (JsonNode c : reply.path("tool_calls")) {
            JsonNode args = c.path("args");
            calls.add(new ToolCall("call_" + System.nanoTime() + "_" + (n++), c.path("name").asText(),
                    args.isObject() ? args : JSON.createObjectNode()));
        }
        return new LlmResponse(reply.path("text").asText(""), calls, calls.isEmpty() ? "end_turn" : "tool_use", in, outTokens);
    }

    /** Which account the CLI uses; strips API-key settings so a stray key or proxy never takes over. */
    Map<String, String> env() throws IOException {
        Map<String, String> env = new java.util.HashMap<>();
        env.put("MAX_THINKING_TOKENS", "0"); // like the API adapter: no extended thinking, so replies stay fast
        if (!configDir.isBlank()) env.put("CLAUDE_CONFIG_DIR", configDir);
        if (!tokenFile.isBlank()) env.put("CLAUDE_CODE_OAUTH_TOKEN", Files.readString(Path.of(tokenFile)).strip());
        return env;
    }

    private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

    private static String runProcess(List<String> command, Map<String, String> env, String stdin)
            throws IOException, InterruptedException {
        // Windows passes one command-line string, and Java doesn't escape quotes inside an argument: escape them so a
        // JSON argument such as --json-schema arrives intact.
        List<String> args = WINDOWS ? command.stream().map(a -> a.replace("\"", "\\\"")).toList() : command;
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.environment().keySet().removeIf(k -> k.startsWith("ANTHROPIC_"));
        pb.environment().putAll(env);
        Process p = pb.start();
        CompletableFuture<String> out = drain(p.getInputStream());
        CompletableFuture<String> err = drain(p.getErrorStream());
        try (var w = p.getOutputStream()) {
            w.write(stdin.getBytes(StandardCharsets.UTF_8));
        }
        if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("timed out after " + TIMEOUT_SECONDS + " s");
        }
        String result = out.join();
        if (result.isBlank()) throw new IOException(err.join().isBlank() ? "no output (exit " + p.exitValue() + ")" : err.join().strip());
        return result;
    }

    private static CompletableFuture<String> drain(java.io.InputStream in) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });
    }

    private static String abbreviate(String s) {
        return s == null ? "" : s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

}
