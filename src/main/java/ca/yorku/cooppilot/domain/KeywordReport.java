package ca.yorku.cooppilot.domain;

import java.util.List;

/**
 * F04 output. backed = on the resume in experience/project text; thin = on the resume only in the
 * skills line; knownNotWorded = the vault shows it but the resume never says it; missing = no evidence.
 */
public record KeywordReport(List<Keyword> backed, List<Keyword> thin, List<Keyword> knownNotWorded, List<Keyword> missing) {

    public KeywordReport {
        backed = List.copyOf(backed);
        thin = List.copyOf(thin);
        knownNotWorded = List.copyOf(knownNotWorded);
        missing = List.copyOf(missing);
    }

    public int total() {
        return backed.size() + thin.size() + knownNotWorded.size() + missing.size();
    }

    /** Share of posting keywords that literally appear on the resume (0..1). */
    public double coverage() {
        return total() == 0 ? 0 : (backed.size() + thin.size()) / (double) total();
    }
}
