package ca.yorku.cooppilot.agent;

import ca.yorku.cooppilot.domain.Posting;

import java.util.List;

/** What the agent is asked to achieve. */
public record AgentGoal(GoalType type, Posting posting, List<String> constraints) {

    public enum GoalType { TAILOR_RESUME, FIT_ONE_PAGE }

    public AgentGoal {
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
    }
}
