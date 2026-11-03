package ca.yorku.cooppilot.service;

import ca.yorku.cooppilot.domain.Keyword;
import ca.yorku.cooppilot.domain.KeywordReport;
import ca.yorku.cooppilot.domain.PostingAnalysis;
import ca.yorku.cooppilot.domain.Resume;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * F04: literal ATS-style keyword coverage. A keyword counts as on the resume only if its term or an
 * alias appears in the text; nothing is inferred from what bullets describe.
 */
@Service
public class KeywordScanner {

    private final VaultService vault;

    public KeywordScanner(VaultService vault) {
        this.vault = vault;
    }

    public KeywordReport scan(Resume resume, PostingAnalysis analysis) {
        String all = ResumeText.all(resume);
        String evidence = ResumeText.evidence(resume);
        Set<String> skills = vault.skills();
        String skillText = String.join(", ", skills);

        List<Keyword> backed = new ArrayList<>(), thin = new ArrayList<>(),
                known = new ArrayList<>(), missing = new ArrayList<>();
        for (Keyword k : analysis.keywords()) {
            if (k.appearsIn(all)) {
                (k.appearsIn(evidence) ? backed : thin).add(k);
            } else if (k.appearsIn(skillText) || vault.search(k.term(), null).stream()
                    .anyMatch(e -> e.getUse().allowedOnResume() && k.appearsIn(e.getTitle() + " " + e.getBody()))) {
                known.add(k);
            } else {
                missing.add(k);
            }
        }
        return new KeywordReport(backed, thin, known, missing);
    }
}
