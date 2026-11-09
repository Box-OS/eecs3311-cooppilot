package ca.yorku.cooppilot.tracking;

/** Stored name of each application state. */
public enum Status {
    TO_APPLY, APPLIED, ASSESSMENT, INTERVIEW, OFFER, REJECTED, GHOSTED;

    /** The State object for this status. */
    public ApplicationState state() {
        return switch (this) {
            case TO_APPLY -> ApplicationState.TO_APPLY;
            case APPLIED -> ApplicationState.APPLIED;
            case ASSESSMENT -> ApplicationState.ASSESSMENT;
            case INTERVIEW -> ApplicationState.INTERVIEW;
            case OFFER -> ApplicationState.OFFER;
            case REJECTED -> ApplicationState.REJECTED;
            case GHOSTED -> ApplicationState.GHOSTED;
        };
    }

    /** The employer answered in some way (used for the response rate). */
    public boolean isResponse() {
        return this == ASSESSMENT || this == INTERVIEW || this == OFFER || this == REJECTED;
    }
}
