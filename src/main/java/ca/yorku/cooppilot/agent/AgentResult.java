package ca.yorku.cooppilot.agent;

import ca.yorku.cooppilot.domain.Resume;

import java.util.Optional;

/** How a run ended, the resume it produced (if any), the model's summary, and the steps used. */
public record AgentResult(Status status, Resume resume, String summary, int steps, int inputTokens, int outputTokens) {

    public enum Status { COMPLETED, STEP_LIMIT, FAILED }

    public Optional<Resume> resumeOpt() {
        return Optional.ofNullable(resume);
    }
}
