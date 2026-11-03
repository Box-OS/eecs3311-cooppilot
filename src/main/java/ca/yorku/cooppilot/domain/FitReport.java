package ca.yorku.cooppilot.domain;

import java.util.List;
import java.util.Optional;

/** F03 output: fit score, matched skills, gaps, and the recommended strategy with the closest saved resume. */
public record FitReport(int score, List<String> matched, List<String> gaps, TailorStrategy strategy, ResumeMatch closest) {

    public FitReport {
        matched = List.copyOf(matched);
        gaps = List.copyOf(gaps);
    }

    public Optional<ResumeMatch> closestResume() {
        return Optional.ofNullable(closest);
    }
}
