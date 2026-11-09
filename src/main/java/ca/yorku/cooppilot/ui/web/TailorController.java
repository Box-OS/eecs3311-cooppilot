package ca.yorku.cooppilot.ui.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Live agent run page (F05/F06): steps arrive over SSE; questions are answered from the page. */
@Controller
public class TailorController {

    private final AgentSessionManager agents;

    public TailorController(AgentSessionManager agents) {
        this.agents = agents;
    }

    @GetMapping("/tailor/{runId}")
    public String page(@PathVariable String runId, Model m) {
        m.addAttribute("runId", runId);
        m.addAttribute("postingId", agents.postingOf(runId));
        return "tailor";
    }

    @GetMapping(path = "/tailor/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter events(@PathVariable String runId) {
        return agents.subscribe(runId);
    }

    @PostMapping("/tailor/{runId}/answer")
    @ResponseBody
    public ResponseEntity<String> answer(@PathVariable String runId, @RequestParam String answer) {
        return agents.answer(runId, answer) ? ResponseEntity.ok("ok")
                : ResponseEntity.status(409).body("The agent is not waiting for an answer.");
    }
}
