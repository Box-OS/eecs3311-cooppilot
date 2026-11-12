package ca.yorku.cooppilot.ui.web;

import ca.yorku.cooppilot.app.CoopPilotFacade;
import ca.yorku.cooppilot.domain.CoverLetter;
import ca.yorku.cooppilot.domain.Resume;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;

/** Library (F10), revision with undo/redo (F08), cover letters (F09), .tex download (F10). */
@Controller
public class ResumeController {

    private final CoopPilotFacade facade;

    public ResumeController(CoopPilotFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/resumes")
    public String library(Model m) {
        m.addAttribute("groups", facade.resumeLibrary());
        m.addAttribute("letters", facade.coverLetters());
        return "resumes";
    }

    @GetMapping("/resumes/{id}")
    public String detail(@PathVariable String id, @RequestParam(required = false) String posting, Model m) {
        m.addAttribute("resume", facade.resume(id));
        m.addAttribute("log", facade.revisionLog(id));
        m.addAttribute("postings", facade.postings().stream().filter(p -> p.isAnalyzed()).toList());
        m.addAttribute("selectedPosting", posting);
        m.addAttribute("pagePercent", Math.round(facade.pageEstimate(id) * 100));
        return "resume";
    }

    @PostMapping("/resumes/{id}/revise")
    public String revise(@PathVariable String id, @RequestParam String instruction, RedirectAttributes flash) {
        return act(id, flash, () -> {
            facade.reviseResume(id, instruction);
            var log = facade.revisionLog(id);
            return log.get(log.size() - 1);
        });
    }

    @PostMapping("/resumes/{id}/condense")
    public String condense(@PathVariable String id, @RequestParam String postingId,
                           @RequestParam(defaultValue = "RULES") ca.yorku.cooppilot.service.CondenseStrategy.Mode mode,
                           RedirectAttributes flash) {
        return act(id, flash, () -> {
            facade.condenseResume(id, postingId, mode);
            var log = facade.revisionLog(id);
            return log.get(log.size() - 1);
        });
    }

    @PostMapping("/resumes/{id}/undo")
    public String undo(@PathVariable String id, RedirectAttributes flash) {
        return act(id, flash, () -> "Undid: " + facade.undo(id));
    }

    @PostMapping("/resumes/{id}/redo")
    public String redo(@PathVariable String id, RedirectAttributes flash) {
        return act(id, flash, () -> "Redid: " + facade.redo(id));
    }

    @PostMapping("/resumes/{id}/letter")
    public String letter(@PathVariable String id, @RequestParam String postingId, RedirectAttributes flash) {
        try {
            CoverLetter c = facade.writeCoverLetter(postingId, id);
            return "redirect:/letters/" + c.name();
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", "Cover letter failed: " + e.getMessage());
            return "redirect:/resumes/" + id;
        }
    }

    @GetMapping("/resumes/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        Resume r = facade.resume(id);
        return attachment(r.getName() + ".tex", r.getLatex(), "application/x-tex");
    }

    @GetMapping("/letters/{name}")
    public String letterPage(@PathVariable String name, Model m) {
        m.addAttribute("letter", facade.coverLetter(name).orElseThrow(() -> new IllegalArgumentException("No cover letter " + name)));
        return "letter";
    }

    @GetMapping("/letters/{name}/download")
    public ResponseEntity<byte[]> downloadLetter(@PathVariable String name) {
        CoverLetter c = facade.coverLetter(name).orElseThrow(() -> new IllegalArgumentException("No cover letter " + name));
        return attachment(c.name() + ".txt", c.body(), MediaType.TEXT_PLAIN_VALUE);
    }

    private String act(String id, RedirectAttributes flash, java.util.function.Supplier<String> action) {
        try {
            flash.addFlashAttribute("message", action.get());
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/resumes/" + id;
    }

    private static ResponseEntity<byte[]> attachment(String filename, String body, String type) {
        String safe = filename.replaceAll("[^A-Za-z0-9_.-]", "");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(safe).build().toString())
                .contentType(MediaType.parseMediaType(type))
                .body(body.getBytes(StandardCharsets.UTF_8));
    }
}
