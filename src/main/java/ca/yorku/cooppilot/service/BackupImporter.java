package ca.yorku.cooppilot.service;

import ca.yorku.cooppilot.domain.EntryKind;
import ca.yorku.cooppilot.domain.EntryUse;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.domain.VaultEntry;
import ca.yorku.cooppilot.persistence.ResumeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * F01 extension: imports a JSON backup so a student starts with an existing vault and resume library.
 * Format: {"vault": [{title, kind, use, date, tags[], body}], "resumes": [{name, family, kind, latex}]}.
 * Vault entries go through VaultService (duplicates merged); resumes are parsed into blocks but do not
 * create vault entries, because the backup's vault already describes the same experience.
 */
@Service
public class BackupImporter {

    public record Result(int vaultAdded, int vaultTotal, int resumesImported, List<String> warnings) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private final VaultService vault;
    private final ResumeRepository resumes;
    private final LatexResumeParser parser;

    public BackupImporter(VaultService vault, ResumeRepository resumes, LatexResumeParser parser) {
        this.vault = vault;
        this.resumes = resumes;
        this.parser = parser;
    }

    public Result importJson(String json) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (IOException e) {
            throw new IllegalArgumentException("Not a valid backup file: " + e.getMessage());
        }
        if (root == null || !root.isObject() || (!root.has("vault") && !root.has("resumes"))) {
            throw new IllegalArgumentException("Not a CoopPilot backup: expected \"vault\" and/or \"resumes\".");
        }
        List<String> warnings = new ArrayList<>();
        int before = vault.all().size();
        int seen = 0;
        for (JsonNode e : root.path("vault")) {
            String title = e.path("title").asText("").trim();
            if (title.isEmpty()) {
                warnings.add("Skipped a vault entry with no title.");
                continue;
            }
            Set<String> tags = new LinkedHashSet<>();
            e.path("tags").forEach(t -> tags.add(t.asText()));
            String kindText = e.path("kind").asText("");
            EntryUse use = use(e.path("use").asText(""), kindText);
            vault.addEntry(new VaultEntry(null, title, kind(kindText), use, e.path("date").asText(""), tags,
                    e.path("body").asText("")));
            seen++;
        }
        int imported = 0;
        for (JsonNode r : root.path("resumes")) {
            String name = r.path("name").asText("").trim();
            String latex = r.path("latex").asText("");
            if (name.isEmpty() || latex.isBlank()) {
                warnings.add("Skipped a resume with no name or LaTeX.");
                continue;
            }
            ParseResult parsed = parser.parse(latex);
            if (parsed.blocks().isEmpty()) {
                warnings.add(name + ": no entries recognized, skipped.");
                continue;
            }
            String id = resumes.findByName(name).map(Resume::getId).orElse(null);
            resumes.save(new Resume(id, name, r.path("family").asText(""), resumeKind(r.path("kind").asText("")),
                    parsed.blocks(), latex));
            imported++;
        }
        return new Result(vault.all().size() - before, seen, imported, warnings);
    }

    static EntryKind kind(String k) {
        return switch (k.toLowerCase(Locale.ROOT)) {
            case "project" -> EntryKind.PROJECT;
            case "work", "job" -> EntryKind.WORK;
            case "course" -> EntryKind.COURSE;
            case "skill" -> EntryKind.SKILL;
            case "planned" -> EntryKind.PLANNED;
            default -> EntryKind.ACTIVITY; // activity, story, context
        };
    }

    /** Context-kind entries are always context-only, whatever the file says. */
    static EntryUse use(String u, String kind) {
        if (kind.equalsIgnoreCase("context")) return EntryUse.CONTEXT_ONLY;
        return switch (u.toLowerCase(Locale.ROOT)) {
            case "letters", "letters_only" -> EntryUse.LETTERS_ONLY;
            case "context", "context_only" -> EntryUse.CONTEXT_ONLY;
            default -> EntryUse.ALL;
        };
    }

    static Resume.Kind resumeKind(String k) {
        return switch (k.toLowerCase(Locale.ROOT)) {
            case "original" -> Resume.Kind.ORIGINAL;
            case "generated" -> Resume.Kind.GENERATED;
            default -> Resume.Kind.TAILORED;
        };
    }
}
