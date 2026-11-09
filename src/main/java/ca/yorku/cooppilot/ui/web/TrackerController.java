package ca.yorku.cooppilot.ui.web;

import ca.yorku.cooppilot.app.CoopPilotFacade;
import ca.yorku.cooppilot.tracking.Status;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Application tracker (F11) and progress dashboard (F12). */
@Controller
public class TrackerController {

    private final CoopPilotFacade facade;

    public TrackerController(CoopPilotFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/tracker")
    public String tracker(Model m) {
        m.addAttribute("apps", facade.applications());
        m.addAttribute("postings", facade.postings());
        m.addAttribute("resumes", facade.resumes());
        return "tracker";
    }

    @PostMapping("/tracker")
    public String add(@RequestParam(required = false) String postingId, @RequestParam(required = false) String company,
                      @RequestParam(required = false) String role, @RequestParam(required = false, defaultValue = "") String resumeName,
                      RedirectAttributes flash) {
        try {
            var a = facade.logApplication(postingId == null || postingId.isBlank() ? null : postingId, company, role, resumeName);
            flash.addFlashAttribute("message", "Logged " + a.getCompany() + ".");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/tracker";
    }

    @PostMapping("/tracker/{id}/status")
    public String status(@PathVariable String id, @RequestParam Status status,
                         @RequestParam(required = false, defaultValue = "") String note, RedirectAttributes flash) {
        try {
            var a = facade.changeStatus(id, status, note);
            flash.addFlashAttribute("message", a.getCompany() + " → " + a.getStatus());
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/tracker";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model m) {
        var s = facade.dashboard();
        m.addAttribute("s", s);
        int max = s.lastSevenDays().values().stream().mapToInt(Integer::intValue).max().orElse(0);
        m.addAttribute("barMax", Math.max(max, Math.max(1, s.goals().daily())));
        return "dashboard";
    }

    @PostMapping("/dashboard/goals")
    public String goals(@RequestParam int daily, @RequestParam int weekly, RedirectAttributes flash) {
        try {
            facade.setGoals(daily, weekly);
            flash.addFlashAttribute("message", "Goals saved.");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/dashboard";
    }
}
