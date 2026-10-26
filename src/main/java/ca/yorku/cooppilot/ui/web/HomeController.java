package ca.yorku.cooppilot.ui.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Landing page of the web GUI. Shows which LLM provider is configured (never the key).
 */
@Controller
public class HomeController {

    private final String provider;

    public HomeController(@Value("${cooppilot.llm.provider}") String provider) {
        this.provider = provider;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("provider", provider);
        return "index";
    }
}
