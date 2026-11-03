package ca.yorku.cooppilot.service;

import ca.yorku.cooppilot.domain.FitReport;
import ca.yorku.cooppilot.domain.Keyword;
import ca.yorku.cooppilot.domain.PostingAnalysis;
import ca.yorku.cooppilot.domain.ResumeMatch;
import ca.yorku.cooppilot.domain.TailorStrategy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * F03: deterministic fit score. Must-have keywords weigh 2, others 1; a keyword is matched when a
 * vault skill names it (term or alias). The strategy follows the closest saved resume's match.
 */
@Service
public class FitScorer {

    public static final int REUSE_THRESHOLD = 75;
    public static final int TWEAK_THRESHOLD = 45;

    public FitReport score(PostingAnalysis a, Set<String> skills, Optional<ResumeMatch> closest) {
        String skillText = String.join(", ", skills);
        List<String> matched = new ArrayList<>();
        List<String> mustGaps = new ArrayList<>();
        List<String> otherGaps = new ArrayList<>();
        int got = 0, total = 0;
        for (Keyword k : a.keywords()) {
            int w = k.mustHave() ? 2 : 1;
            total += w;
            if (k.appearsIn(skillText)) {
                got += w;
                matched.add(k.term());
            } else {
                (k.mustHave() ? mustGaps : otherGaps).add(k.term());
            }
        }
        List<String> gaps = new ArrayList<>(mustGaps);
        gaps.addAll(otherGaps);
        int score = total == 0 ? 0 : Math.round(100f * got / total);
        return new FitReport(score, matched, gaps, strategy(closest), closest.orElse(null));
    }

    static TailorStrategy strategy(Optional<ResumeMatch> closest) {
        if (closest.isEmpty()) return TailorStrategy.GENERATE;
        int m = closest.get().matchPercent();
        if (m >= REUSE_THRESHOLD) return TailorStrategy.REUSE;
        return m >= TWEAK_THRESHOLD ? TailorStrategy.TWEAK : TailorStrategy.GENERATE;
    }
}
