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

    public Optional<VaultEntry> get(String id) {
        return repo.findById(id);
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

    /**
     * F06: files the student's answer so the agent never has to ask again. If {@code topic} names an
     * existing entry, the fact is added to its details; otherwise a new entry is created. A "no" (the student
     * hasn't used it) goes into one context-only entry instead, so it is remembered but never counts as evidence
     * of the skill, on a resume or in the keyword scan.
     */
    public VaultEntry fileAnswer(String question, String answer, String topic, EntryKind kindIfNew) {
        String fact = "- " + question.trim().replaceAll("\\?$", "") + ": " + answer.trim();
        if (isNo(answer)) {
            VaultEntry e = repo.findAll().stream().filter(x -> x.getTitle().equals(NOT_USED)).findFirst()
                    .orElseGet(() -> addEntry(new VaultEntry(null, NOT_USED, EntryKind.ACTIVITY, EntryUse.CONTEXT_ONLY, "", Set.of(), "")));
            e.setBody(e.getBody().isBlank() ? fact : e.getBody() + "\n" + fact);
            repo.save(e);
            return e;
        }
        Optional<VaultEntry> target = topic == null || topic.isBlank() ? Optional.empty()
                : repo.findAll().stream().filter(e -> norm(e.getTitle()).contains(norm(topic))
                        || norm(topic).contains(norm(e.getTitle()))).findFirst();
        if (target.isPresent()) {
            VaultEntry e = target.get();
            e.setBody(e.getBody().isBlank() ? fact : e.getBody() + "\n" + fact);
            repo.save(e);
            return e;
        }
        String title = topic == null || topic.isBlank() ? question.trim() : topic.trim();
        return addEntry(new VaultEntry(null, title, kindIfNew == null ? EntryKind.ACTIVITY : kindIfNew,
                EntryUse.ALL, "", Set.of(), fact));
    }

    /** Title of the context-only entry that holds "no, I haven't used it" answers. */
    public static final String NOT_USED = "Not used (answers to CoopPilot's questions)";

    private static final java.util.regex.Pattern NO = java.util.regex.Pattern.compile(
            "^\\s*(no|nope|never|not (really|yet)|i (have not|haven't|have never|never|did not|didn't|do not|don't))\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    static boolean isNo(String answer) {
        return answer != null && NO.matcher(answer).find();
    }

    /** True if the student has already answered a question about this term (any filed answer line mentions it). */
    public boolean answeredAbout(java.util.function.Predicate<String> mentions) {
        for (VaultEntry e : repo.findAll()) {
            for (String line : e.getBody().split("\n")) {
                if (line.startsWith("- ") && line.contains(": ") && mentions.test(line.substring(0, line.indexOf(": ")))) return true;
            }
        }
        return false;
    }

    /** An answer already filed for this exact question, if any (memory lookup before asking). */
    public Optional<String> knownAnswer(String question) {
        String q = "- " + question.trim().replaceAll("\\?$", "") + ": ";
        for (VaultEntry e : repo.findAll()) {
            for (String line : e.getBody().split("\n")) {
                if (line.toLowerCase(Locale.ROOT).startsWith(q.toLowerCase(Locale.ROOT))) {
                    return Optional.of(line.substring(q.length()));
                }
            }
        }
        return Optional.empty();
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
