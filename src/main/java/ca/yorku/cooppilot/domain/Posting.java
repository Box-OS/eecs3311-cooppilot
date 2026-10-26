package ca.yorku.cooppilot.domain;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** A co-op job posting as imported by the student; {@code analysis} is filled in by F02. */
public class Posting {

    private final String id;
    private String company;
    private String role;
    private final String rawText;
    private String term;
    private LocalDate deadline;
    private PostingAnalysis analysis;

    public Posting(String id, String company, String role, String rawText, String term, LocalDate deadline) {
        if (rawText == null || rawText.isBlank()) throw new IllegalArgumentException("posting text must not be blank");
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        this.company = company == null ? "" : company.trim();
        this.role = role == null ? "" : role.trim();
        this.rawText = rawText;
        this.term = term == null ? "" : term;
        this.deadline = deadline;
    }

    public boolean isAnalyzed() {
        return analysis != null;
    }

    public String getId() { return id; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getRawText() { return rawText; }
    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }
    public Optional<LocalDate> getDeadline() { return Optional.ofNullable(deadline); }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public Optional<PostingAnalysis> getAnalysis() { return Optional.ofNullable(analysis); }
    public void setAnalysis(PostingAnalysis analysis) { this.analysis = analysis; }
}
