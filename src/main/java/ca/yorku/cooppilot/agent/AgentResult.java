package ca.yorku.cooppilot.agent;

import ca.yorku.cooppilot.domain.Resume;

import java.util.Optional;

/**
 * How a run ended, the resume it produced (if any), whether that resume is stored in the library
 * (the final resume, or a partial draft kept after an early stop), the model's summary, and the steps used.
 */
public record AgentResult(Status status, Resume resume, boolean resumeSaved, String summary, int steps,
                          int inputTokens, int outputTokens) {

    public enum Status { COMPLETED, STEP_LIMIT, FAILED }

    public Optional<Resume> resumeOpt() {
        return Optional.ofNullable(resume);
    }
}
