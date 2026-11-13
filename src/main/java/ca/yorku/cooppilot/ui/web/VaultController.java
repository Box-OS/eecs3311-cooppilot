package ca.yorku.cooppilot.ui.web;

import ca.yorku.cooppilot.app.CoopPilotFacade;
import ca.yorku.cooppilot.domain.EntryKind;
import ca.yorku.cooppilot.domain.EntryUse;
import ca.yorku.cooppilot.domain.VaultEntry;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Vault page (F01): import a .tex resume, add entries by hand, list and search the vault. */
@Controller
public class VaultController {

    private final CoopPilotFacade facade;

    public VaultController(CoopPilotFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/vault")
    public String vaultPage(@RequestParam(name = "q", required = false) String q,
                            @RequestParam(name = "tag", required = false) String tag, Model m) {
        m.addAttribute("entries", facade.vaultEntries(q, tag));
        m.addAttribute("tag", tag == null ? "" : tag);
        m.addAttribute("resumes", facade.resumes());
        m.addAttribute("q", q == null ? "" : q);
        m.addAttribute("kinds", EntryKind.values());
        m.addAttribute("uses", EntryUse.values());
        return "vault";
    }

    @PostMapping("/vault/import")
    public String importResume(@RequestParam("file") MultipartFile file,
                               @RequestParam(name = "name", required = false) String name,
                               RedirectAttributes flash) throws IOException {
        if (file.isEmpty()) {
            flash.addFlashAttribute("error", "Choose a .tex file first.");
            return "redirect:/vault";
        }
        String latex = new String(file.getBytes(), StandardCharsets.UTF_8);
        CoopPilotFacade.ImportOutcome out = facade.importResume(latex, name);
        if (out.resume() == null) {
            flash.addFlashAttribute("error", "Nothing imported.");
        } else {
            flash.addFlashAttribute("message", "Imported " + out.resume().getBlocks().size() + " blocks as "
                    + out.resume().getName() + "; " + out.newVaultEntries() + " new vault entries.");
        }
        flash.addFlashAttribute("warnings", out.parse().warnings());
        return "redirect:/vault";
    }

    @PostMapping("/vault/import-backup")
    public String importBackup(@RequestParam("file") MultipartFile file, RedirectAttributes flash) throws IOException {
        if (file.isEmpty()) {
            flash.addFlashAttribute("error", "Choose a backup .json file first.");
            return "redirect:/vault";
        }
        try {
            var r = facade.importBackup(new String(file.getBytes(), StandardCharsets.UTF_8));
            flash.addFlashAttribute("message", "Backup imported: " + r.vaultAdded() + " new vault entries (of "
                    + r.vaultTotal() + "), " + r.resumesImported() + " resumes.");
            flash.addFlashAttribute("warnings", r.warnings());
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/vault";
    }

    @PostMapping("/vault/entries")
    public String addEntry(@RequestParam String title, @RequestParam EntryKind kind, @RequestParam EntryUse use,
                           @RequestParam(required = false) String date, @RequestParam(required = false) String tags,
                           @RequestParam(required = false) String body, RedirectAttributes flash) {
        if (title == null || title.isBlank()) {
            flash.addFlashAttribute("error", "An entry needs a title.");
            return "redirect:/vault";
        }
        Set<String> tagSet = tags == null ? Set.of() : Arrays.stream(tags.split(","))
                .map(String::trim).filter(t -> !t.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
        VaultEntry candidate = new VaultEntry(null, title, kind, use, date, tagSet, body);
        VaultEntry saved = facade.addVaultEntry(candidate);
        if (!saved.getId().equals(candidate.getId())) {
            // UC01 5a: duplicate (e.g. the same skill with different spacing or case)
            flash.addFlashAttribute("error", "\"" + title.trim() + "\" is already in the vault as \"" + saved.getTitle()
                    + "\"; no new entry was added (its tags were merged).");
        } else {
            flash.addFlashAttribute("message", "Saved \"" + saved.getTitle() + "\".");
        }
        return "redirect:/vault";
    }

    @GetMapping("/vault/{id}/edit")
    public String editPage(@PathVariable String id, Model m) {
        m.addAttribute("e", facade.vaultEntry(id));
        m.addAttribute("kinds", EntryKind.values());
        m.addAttribute("uses", EntryUse.values());
        return "vault-edit";
    }

    @PostMapping("/vault/{id}")
    public String update(@PathVariable String id, @RequestParam String title, @RequestParam EntryKind kind,
                         @RequestParam EntryUse use, @RequestParam(required = false) String date,
                         @RequestParam(required = false) String tags, @RequestParam(required = false) String body,
                         RedirectAttributes flash) {
        if (title == null || title.isBlank()) {
            flash.addFlashAttribute("error", "An entry needs a title.");
            return "redirect:/vault/" + id + "/edit";
        }
        Set<String> tagSet = tags == null ? Set.of() : Arrays.stream(tags.split(","))
                .map(String::trim).filter(t -> !t.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
        facade.updateVaultEntry(id, title, kind, use, date, tagSet, body);
        flash.addFlashAttribute("message", "Updated \"" + title.trim() + "\".");
        return "redirect:/vault";
    }

    @PostMapping("/vault/{id}/delete")
    public String delete(@PathVariable String id, RedirectAttributes flash) {
        String title = facade.vaultEntry(id).getTitle();
        facade.deleteVaultEntry(id);
        flash.addFlashAttribute("message", "Deleted \"" + title + "\".");
        return "redirect:/vault";
    }
}
