package ca.yorku.cooppilot.agent;

import java.util.Optional;

/** How the agent asks the student a question; web and CLI implement it differently. Empty = no answer. */
public interface QuestionHandler {

    Optional<String> ask(String question);

    /** For runs where nobody can answer (tests, batch). */
    QuestionHandler NONE = q -> Optional.empty();
}
