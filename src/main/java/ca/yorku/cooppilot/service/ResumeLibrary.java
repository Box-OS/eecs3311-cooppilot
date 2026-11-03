package ca.yorku.cooppilot.service;

import ca.yorku.cooppilot.domain.Keyword;
import ca.yorku.cooppilot.domain.PostingAnalysis;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.ResumeMatch;
import ca.yorku.cooppilot.persistence.ResumeRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** F10 / F03: the saved-resume library and "which saved resume already fits this posting best". */
@Service
public class ResumeLibrary {

    private final ResumeRepository repo;
    private final VaultService vault;

    public ResumeLibrary(ResumeRepository repo, VaultService vault) {
        this.repo = repo;
        this.vault = vault;
    }

    /**
     * Match % = of the posting keywords the student actually has (vault skills), the share this resume
     * shows, with must-haves counted twice. Resumes with [placeholders] are never suggested.
     */
    public Optional<ResumeMatch> findClosest(PostingAnalysis a) {
        String skillText = String.join(", ", vault.skills());
        List<Keyword> relevant = a.keywords().stream().filter(k -> k.appearsIn(skillText)).toList();
        if (relevant.isEmpty()) relevant = a.keywords();
        final List<Keyword> keys = relevant;
        return repo.findAll().stream()
                .filter(r -> !r.hasPlaceholders())
                .map(r -> new ResumeMatch(r, matchPercent(r, keys)))
                .max(Comparator.comparingInt(ResumeMatch::matchPercent));
    }

    static int matchPercent(Resume r, List<Keyword> keys) {
        if (keys.isEmpty()) return 0;
        String text = ResumeText.all(r);
        int got = 0, total = 0;
        for (Keyword k : keys) {
            int w = k.mustHave() ? 2 : 1;
            total += w;
            if (k.appearsIn(text)) got += w;
        }
        return Math.round(100f * got / total);
    }

    public Map<String, List<Resume>> groupByFamily() {
        Map<String, List<Resume>> out = new TreeMap<>();
        for (Resume r : repo.findAll()) {
            out.computeIfAbsent(r.getFamily().isBlank() ? "general" : r.getFamily(), f -> new java.util.ArrayList<>()).add(r);
        }
        return new LinkedHashMap<>(out);
    }

    public Optional<Resume> byId(String id) {
        return repo.findById(id);
    }

    public Optional<Resume> byName(String name) {
        return repo.findByName(name);
    }

    public void save(Resume r) {
        repo.save(r);
    }

    public List<Resume> all() {
        return repo.findAll();
    }

    /** True when the name is already used by a different resume. */
    public boolean nameTaken(String name, Set<String> exceptIds) {
        return repo.findByName(name).filter(r -> !exceptIds.contains(r.getId())).isPresent();
    }
}
