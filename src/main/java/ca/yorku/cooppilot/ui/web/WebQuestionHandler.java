package ca.yorku.cooppilot.ui.web;

import ca.yorku.cooppilot.agent.QuestionHandler;

import java.util.Optional;

/** Asks the student in the browser and waits (up to 5 minutes) for the answer posted from the page. */
public class WebQuestionHandler implements QuestionHandler {

    private final AgentSessionManager manager;
    private final AgentSessionManager.WebRun run;
    private final long timeoutSeconds;

    WebQuestionHandler(AgentSessionManager manager, AgentSessionManager.WebRun run, long timeoutSeconds) {
        this.manager = manager;
        this.run = run;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public Optional<String> ask(String question) {
        return manager.waitForAnswer(run, question, timeoutSeconds);
    }
}
