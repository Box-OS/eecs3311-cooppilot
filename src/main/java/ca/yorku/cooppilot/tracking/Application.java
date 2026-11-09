package ca.yorku.cooppilot.tracking;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** State pattern — Context. One job application; its behaviour on status changes comes from its state. */
public class Application {

    private final String id;
    private String company;
    private String role;
    private String postingId;
    private String resumeName;
    private String coverLetterName;
    private LocalDate appliedOn;
    private LocalDate followUpOn;
    private String notes;
    private ApplicationState state;
    private final List<StatusEvent> history = new ArrayList<>();

    public Application(String id, String company, String role, String postingId, String resumeName) {
        if (company == null || company.isBlank()) throw new IllegalArgumentException("company is required");
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.company = company.trim();
        this.role = role == null ? "" : role.trim();
        this.postingId = postingId;
        this.resumeName = resumeName == null ? "" : resumeName;
        this.coverLetterName = "";
        this.notes = "";
        this.state = ApplicationState.TO_APPLY;
    }

    /** Delegates the transition to the current state and records it. */
    public StatusEvent transitionTo(Status target, LocalDate today, String note) {
        Status from = state.status();
        state = state.moveTo(this, target, today);
        StatusEvent e = new StatusEvent(from, target, today, note == null ? "" : note);
        history.add(e);
        return e;
    }

    public boolean followUpDue(LocalDate today) {
        return followUpOn != null && !followUpOn.isAfter(today);
    }

    /** Restores persisted state without running entry side effects. */
    public void restore(Status status, List<StatusEvent> events) {
        this.state = status.state();
        history.clear();
        history.addAll(events);
    }

    public String getId() { return id; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getPostingId() { return postingId; }
    public String getResumeName() { return resumeName; }
    public void setResumeName(String resumeName) { this.resumeName = resumeName; }
    public String getCoverLetterName() { return coverLetterName; }
    public void setCoverLetterName(String n) { this.coverLetterName = n == null ? "" : n; }
    public LocalDate getAppliedOn() { return appliedOn; }
    public void setAppliedOn(LocalDate d) { this.appliedOn = d; }
    public LocalDate getFollowUpOn() { return followUpOn; }
    public void setFollowUpOn(LocalDate d) { this.followUpOn = d; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes == null ? "" : notes; }
    public ApplicationState getState() { return state; }
    public Status getStatus() { return state.status(); }
    public List<StatusEvent> getHistory() { return List.copyOf(history); }
}
