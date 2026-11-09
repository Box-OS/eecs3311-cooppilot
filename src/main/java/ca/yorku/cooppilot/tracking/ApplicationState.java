package ca.yorku.cooppilot.tracking;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * State pattern — State. Each state decides which moves are legal and what happens on entry
 * (e.g. entering APPLIED stamps the date and schedules a follow-up). The Application (context)
 * delegates every transition to its current state, so there is no status switch in the tracker.
 */
public abstract class ApplicationState {

    public static final int FOLLOW_UP_DAYS = 10;

    public static final ApplicationState TO_APPLY = new ApplicationState(Status.TO_APPLY, EnumSet.of(Status.APPLIED)) {};

    public static final ApplicationState APPLIED = new ApplicationState(Status.APPLIED,
            EnumSet.of(Status.ASSESSMENT, Status.INTERVIEW, Status.OFFER, Status.REJECTED, Status.GHOSTED)) {
        @Override
        void onEnter(Application app, LocalDate today) {
            if (app.getAppliedOn() == null) app.setAppliedOn(today);
            app.setFollowUpOn(today.plusDays(FOLLOW_UP_DAYS));
        }
    };

    public static final ApplicationState ASSESSMENT = new ApplicationState(Status.ASSESSMENT,
            EnumSet.of(Status.INTERVIEW, Status.OFFER, Status.REJECTED, Status.GHOSTED)) {};

    public static final ApplicationState INTERVIEW = new ApplicationState(Status.INTERVIEW,
            EnumSet.of(Status.INTERVIEW, Status.OFFER, Status.REJECTED, Status.GHOSTED)) {};

    /** Terminal. */
    public static final ApplicationState OFFER = new ApplicationState(Status.OFFER, EnumSet.noneOf(Status.class)) {
        @Override
        void onEnter(Application app, LocalDate today) { app.setFollowUpOn(null); }
    };

    /** Terminal. */
    public static final ApplicationState REJECTED = new ApplicationState(Status.REJECTED, EnumSet.noneOf(Status.class)) {
        @Override
        void onEnter(Application app, LocalDate today) { app.setFollowUpOn(null); }
    };

    /** A late reply can still revive a ghosted application. */
    public static final ApplicationState GHOSTED = new ApplicationState(Status.GHOSTED,
            EnumSet.of(Status.ASSESSMENT, Status.INTERVIEW, Status.OFFER, Status.REJECTED)) {
        @Override
        void onEnter(Application app, LocalDate today) { app.setFollowUpOn(null); }
    };

    private final Status status;
    private final Set<Status> next;

    private ApplicationState(Status status, Set<Status> next) {
        this.status = status;
        this.next = next;
    }

    public Status status() {
        return status;
    }

    public boolean canMoveTo(Status target) {
        return next.contains(target);
    }

    public Set<Status> allowedNext() {
        return Set.copyOf(next);
    }

    public boolean isTerminal() {
        return next.isEmpty();
    }

    /** Moves the application to {@code target} if legal; returns the new state. */
    ApplicationState moveTo(Application app, Status target, LocalDate today) {
        if (!canMoveTo(target)) {
            throw new IllegalStateException("Cannot go from " + status + " to " + target
                    + (isTerminal() ? " (" + status + " is final)" : "; allowed: " + next));
        }
        ApplicationState s = target.state();
        s.onEnter(app, today);
        return s;
    }

    /** Hook for side effects when a state is entered. */
    void onEnter(Application app, LocalDate today) {}

    @Override
    public String toString() {
        return status.name();
    }
}
