package ca.yorku.cooppilot.domain;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One fact source in the Experience Vault: the agent's long-term memory of the student's projects, jobs,
 * courses, activities and skills.
 */
public class VaultEntry {

    private final String id;
    private String title;
    private EntryKind kind;
    private EntryUse use;
    private String date;
    private final Set<String> tags = new LinkedHashSet<>();
    private String body;

    public VaultEntry(String id, String title, EntryKind kind, EntryUse use, String date, Set<String> tags, String body) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        this.title = requireText(title, "title");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.use = use == null ? EntryUse.ALL : use;
        this.date = date == null ? "" : date;
        if (tags != null) tags.forEach(this::addTag);
        this.body = body == null ? "" : body;
    }

    public static VaultEntry of(String title, EntryKind kind, String body) {
        return new VaultEntry(null, title, kind, EntryUse.ALL, "", Set.of(), body);
    }

    /** A plain skill entry ("Knows Docker"). */
    public static VaultEntry skill(String name) {
        return new VaultEntry(null, "Knows " + name.trim(), EntryKind.SKILL, EntryUse.ALL, "", Set.of(name), "");
    }

    /** The skill name for SKILL entries ("Knows Docker" -> "Docker"), else the title. */
    public String skillName() {
        return kind == EntryKind.SKILL && title.startsWith("Knows ") ? title.substring(6).trim() : title;
    }

    /** Case-insensitive match on title, body and tags. */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.toLowerCase(Locale.ROOT);
        return title.toLowerCase(Locale.ROOT).contains(q)
                || body.toLowerCase(Locale.ROOT).contains(q)
                || tags.stream().anyMatch(t -> t.contains(q));
    }

    public void addTag(String tag) {
        if (tag != null && !tag.isBlank()) tags.add(tag.trim().toLowerCase(Locale.ROOT));
    }

    private static String requireText(String s, String field) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return s.trim();
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = requireText(title, "title"); }
    public EntryKind getKind() { return kind; }
    public void setKind(EntryKind kind) { this.kind = Objects.requireNonNull(kind); }
    public EntryUse getUse() { return use; }
    public void setUse(EntryUse use) { this.use = Objects.requireNonNull(use); }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date == null ? "" : date; }
    public Set<String> getTags() { return Set.copyOf(tags); }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body == null ? "" : body; }

    @Override
    public String toString() {
        return kind + " " + title;
    }
}
