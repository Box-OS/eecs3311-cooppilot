package ca.yorku.cooppilot.service;

import ca.yorku.cooppilot.domain.EntryKind;
import ca.yorku.cooppilot.domain.EntryUse;
import ca.yorku.cooppilot.domain.ResumeBlock;
import ca.yorku.cooppilot.domain.Section;
import ca.yorku.cooppilot.domain.VaultEntry;
import ca.yorku.cooppilot.persistence.VaultRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** The Experience Vault: the agent's long-term memory, with the rules that keep it clean. */
@Service
public class VaultService {

    private final VaultRepository repo;

    public VaultService(VaultRepository repo) {
        this.repo = repo;
    }

    /**
     * Adds an entry unless an equivalent one exists (same kind and title, case-insensitive; for skills,
     * the same skill name). Returns the entry that is now in the vault.
     */
    public VaultEntry addEntry(VaultEntry e) {
        Optional<VaultEntry> existing = findDuplicate(e);
        if (existing.isPresent()) {
            VaultEntry kept = existing.get();
            e.getTags().forEach(kept::addTag);
            if (kept.getBody().isBlank() && !e.getBody().isBlank()) kept.setBody(e.getBody());
            repo.save(kept);
            return kept;
        }
        repo.save(e);
        return e;
    }

    public void update(VaultEntry e) {
        repo.save(e);
    }

    public void delete(String id) {
        repo.delete(id);
    }

    public List<VaultEntry> all() {
        return repo.findAll().stream()
                .sorted(Comparator.comparing(VaultEntry::getKind).thenComparing(VaultEntry::getTitle, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Entries matching the text query and carrying every given tag. */
    public List<VaultEntry> search(String query, Set<String> tags) {
        Set<String> wanted = tags == null ? Set.of()
                : tags.stream().map(t -> t.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        return all().stream()
                .filter(e -> e.matches(query))
                .filter(e -> e.getTags().containsAll(wanted))
                .toList();
    }

    /** Every skill the student has: SKILL entries plus tags on resume-usable entries. */
    public Set<String> skills() {
        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (VaultEntry e : repo.findAll()) {
            if (e.getKind() == EntryKind.SKILL) out.add(e.skillName());
            else if (e.getKind() != EntryKind.PLANNED && e.getUse() != EntryUse.CONTEXT_ONLY) out.addAll(e.getTags());
        }
        return out;
    }

    /** Creates vault entries for imported resume blocks and skills (F01). Returns how many were new. */
    public int importFromResume(ParseResult parsed) {
        int before = repo.findAll().size();
        for (ResumeBlock b : parsed.blocks()) {
            if (b.getSection() == Section.SKILLS) continue;
            VaultEntry e = new VaultEntry(null, entryTitle(b), kindFor(b.getSection()), EntryUse.ALL,
                    dateText(b), b.getTags(), b.getBullets().stream().map(LatexText::plain)
                    .map(s -> "- " + s).collect(Collectors.joining("\n")));
            addEntry(e);
        }
        parsed.skills().forEach(s -> addEntry(VaultEntry.skill(s)));
        return repo.findAll().size() - before;
    }

    private Optional<VaultEntry> findDuplicate(VaultEntry e) {
        return repo.findByKind(e.getKind()).stream()
                .filter(x -> x.getKind() == EntryKind.SKILL
                        ? norm(x.skillName()).equals(norm(e.skillName()))
                        : norm(x.getTitle()).equals(norm(e.getTitle())))
                .findFirst();
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+#]", "");
    }

    private static String entryTitle(ResumeBlock b) {
        return b.getSubtitle().isBlank() || b.getSection() == Section.PROJECTS
                ? b.getTitle() : b.getTitle() + " — " + b.getSubtitle();
    }

    private static EntryKind kindFor(Section s) {
        return switch (s) {
            case EXPERIENCE -> EntryKind.WORK;
            case PROJECTS -> EntryKind.PROJECT;
            case EDUCATION -> EntryKind.COURSE;
            case LEADERSHIP -> EntryKind.ACTIVITY;
            case SKILLS -> EntryKind.SKILL;
        };
    }

    private static String dateText(ResumeBlock b) {
        if (b.getStartDate() == null) return "";
        String end = b.getEndDate() == null ? "present" : b.getEndDate().toString();
        return b.getStartDate().equals(b.getEndDate()) ? b.getStartDate().toString() : b.getStartDate() + " to " + end;
    }
}
