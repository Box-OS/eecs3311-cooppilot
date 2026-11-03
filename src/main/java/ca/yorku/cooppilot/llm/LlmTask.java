package ca.yorku.cooppilot.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * Template Method pattern. {@link #run()} fixes the skeleton every single-shot LLM job follows:
 * build the request, call the model, parse the reply, validate it, and retry with the problems
 * listed when parsing or validation fails. Subclasses fill in only the varying steps.
 *
 * @param <T> what the task produces (a posting analysis, a cover letter, a revised resume...)
 */
public abstract class LlmTask<T> {

    protected final LLMClient llm;
    private final int maxRetries;

    protected LlmTask(LLMClient llm, int maxRetries) {
        this.llm = llm;
        this.maxRetries = maxRetries;
    }

    /** The fixed algorithm. Final so subclasses cannot skip validation or retries. */
    public final T run() {
        List<String> problems = List.of();
        List<String> history = new ArrayList<>();
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            LlmRequest request = attempt == 0 ? buildRequest() : withFeedback(buildRequest(), problems);
            LlmResponse response = llm.complete(request);
            T result;
            try {
                result = parse(response);
            } catch (RuntimeException e) {
                problems = List.of("Your reply could not be parsed: " + e.getMessage());
                history.add("attempt " + (attempt + 1) + ": " + problems.get(0));
                continue;
            }
            problems = validate(result);
            if (problems.isEmpty()) return result;
            history.add("attempt " + (attempt + 1) + ": " + String.join("; ", problems));
        }
        throw new LlmTaskException(getClass().getSimpleName() + " failed after " + (maxRetries + 1)
                + " attempts", history);
    }

    /** Step 1: the prompt for this task. */
    protected abstract LlmRequest buildRequest();

    /** Step 2: turn the model's reply into the result type; throw if it is malformed. */
    protected abstract T parse(LlmResponse response);

    /** Step 3 (hook): rule checks on the parsed result. Empty list = accepted. */
    protected List<String> validate(T result) {
        return List.of();
    }

    /** Appends the problems from the last attempt so the model can correct itself. */
    private static LlmRequest withFeedback(LlmRequest req, List<String> problems) {
        String last = req.messages().get(req.messages().size() - 1).content();
        String fixed = last + "\n\nYour previous answer had these problems; fix them:\n- " + String.join("\n- ", problems);
        return LlmRequest.simple(req.system(), fixed, req.maxTokens());
    }

    /** The task could not produce a valid result. */
    public static class LlmTaskException extends RuntimeException {
        private final List<String> attempts;

        public LlmTaskException(String message, List<String> attempts) {
            super(message + (attempts.isEmpty() ? "" : ": " + attempts.get(attempts.size() - 1)));
            this.attempts = List.copyOf(attempts);
        }

        public List<String> attempts() {
            return attempts;
        }
    }
}
