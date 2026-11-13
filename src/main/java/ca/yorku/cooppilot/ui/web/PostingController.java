package ca.yorku.cooppilot.ui.web;

import ca.yorku.cooppilot.app.CoopPilotFacade;
import ca.yorku.cooppilot.domain.Posting;
import ca.yorku.cooppilot.domain.Resume;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.List;

/** Postings (F02), fit and strategy (F03), keyword scan (F04), reuse as-is (F10). */
@Controller
public class PostingController {

    private final CoopPilotFacade facade;
    private final AgentSessionManager agents;

    public PostingController(CoopPilotFacade facade, AgentSessionManager agents) {
        this.facade = facade;
        this.agents = agents;
    }

    @GetMapping("/postings")
    public String list(Model m) {
        m.addAttribute("postings", facade.postings());
        return "postings";
    }

    @PostMapping("/postings")
    public String add(@RequestParam String text, @RequestParam(required = false) String company,
                      @RequestParam(required = false) String role,
                      @RequestParam(required = false, defaultValue = "false") boolean analyze, RedirectAttributes flash) {
        if (text == null || text.isBlank()) {
            flash.addFlashAttribute("error", "Paste the posting text first.");
            return "redirect:/postings";
        }
        Posting p = facade.importPosting(text, company, role);
        if (analyze) return analyze(p.getId(), flash);
        return "redirect:/postings/" + p.getId();
    }

    @PostMapping("/postings/{id}/analyze")
    public String analyze(@PathVariable String id, RedirectAttributes flash) {
        try {
            facade.analyzePosting(id);
            flash.addFlashAttribute("message", "Posting analyzed.");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", "Analysis failed: " + e.getMessage());
        }
        return "redirect:/postings/" + id;
    }

    @GetMapping("/postings/{id}")
    public String detail(@PathVariable String id, @RequestParam(required = false) String scan, Model m) {
        Posting p = facade.posting(id);
        m.addAttribute("posting", p);
        m.addAttribute("kwText", p.getAnalysis().map(a -> a.keywords().stream()
                .map(k -> k.term() + (k.aliases().isEmpty() && !k.mustHave() ? "" : " | " + String.join(", ", k.aliases()))
                        + (k.mustHave() ? " | must" : ""))
                .collect(java.util.stream.Collectors.joining("\n"))).orElse(""));
        List<Resume> resumes = facade.resumes().stream().sorted(Comparator.comparing(Resume::getName)).toList();
        m.addAttribute("resumes", resumes);
        if (p.isAnalyzed()) {
            var fit = facade.analyzeFit(id);
            m.addAttribute("fit", fit);
            String scanId = scan != null ? scan : fit.closestResume().map(x -> x.resume().getId())
                    .orElse(resumes.isEmpty() ? null : resumes.get(0).getId());
            if (scanId != null) {
                m.addAttribute("scanResume", facade.resume(scanId));
                m.addAttribute("scan", facade.scanKeywords(scanId, id));
            }
        }
        return "posting";
    }

    @PostMapping("/postings/{id}/analysis")
    public String saveAnalysis(@PathVariable String id, @RequestParam(required = false) String company,
                               @RequestParam(required = false) String role, @RequestParam(required = false) String term,
                               @RequestParam(required = false) String deadline, @RequestParam(required = false) String mustHaves,
                               @RequestParam(required = false) String niceToHaves, @RequestParam(required = false) String keywords,
                               @RequestParam(required = false) String documents, RedirectAttributes flash) {
        try {
            facade.saveAnalysis(id, company, role, term, deadline, mustHaves, niceToHaves, keywords, documents);
            flash.addFlashAttribute("message", "Analysis saved.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/postings/" + id;
    }

    @PostMapping("/postings/{id}/tailor")
    public String tailor(@PathVariable String id) {
        return "redirect:/tailor/" + agents.start(id);
    }

    @PostMapping("/postings/{id}/reuse")
    public String reuse(@PathVariable String id, @RequestParam String resumeId, RedirectAttributes flash) {
        try {
            Resume r = facade.reuseResume(id, resumeId);
            flash.addFlashAttribute("message", "Saved as " + r.getName() + ".");
            return "redirect:/resumes/" + r.getId() + "?posting=" + id;
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/postings/" + id;
        }
    }
}
