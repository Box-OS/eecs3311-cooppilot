package ca.yorku.cooppilot.ui.web;

import ca.yorku.cooppilot.app.CoopPilotApplication;
import ca.yorku.cooppilot.app.CoopPilotFacade;
import ca.yorku.cooppilot.domain.Posting;
import ca.yorku.cooppilot.domain.Resume;
import ca.yorku.cooppilot.llm.CoverLetterTestSupport;
import ca.yorku.cooppilot.llm.LLMClient;
import ca.yorku.cooppilot.llm.MockLlmClient;
import ca.yorku.cooppilot.llm.PostingAnalysisTaskTest;
import ca.yorku.cooppilot.persistence.ResumeRepository;
import ca.yorku.cooppilot.persistence.VaultRepository;
import ca.yorku.cooppilot.tracking.Application;
import ca.yorku.cooppilot.tracking.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Clicks through every GUI page with the mock LLM. */
@SpringBootTest(classes = CoopPilotApplication.class)
@AutoConfigureMockMvc
class WebPagesTest {

    @Autowired MockMvc mvc;
    @Autowired CoopPilotFacade facade;
    @Autowired LLMClient llm;
    @Autowired ResumeRepository resumeRepo;
    @Autowired VaultRepository vaultRepo;
    @Autowired ApplicationRepository appRepo;

    @BeforeEach
    void clean() {
        vaultRepo.findAll().forEach(e -> vaultRepo.delete(e.getId()));
        resumeRepo.findAll().forEach(r -> resumeRepo.delete(r.getId()));
        appRepo.findAll().forEach(a -> appRepo.delete(a.getId()));
    }

    @Test
    void fullWalkthrough() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("Dashboard")));

        byte[] tex = Files.readAllBytes(Path.of("samples/sample-resume.tex"));
        mvc.perform(multipart("/vault/import").file(new MockMultipartFile("file", "r.tex", "text/plain", tex)).param("name", "CV_Web"))
                .andExpect(status().is3xxRedirection());
        Resume resume = resumeRepo.findByName("CV_Web").orElseThrow();

        ((MockLlmClient) llm).enqueueText(PostingAnalysisTaskTest.GOOD);
        MvcResult added = mvc.perform(post("/postings").param("text", Files.readString(Path.of("samples/sample-posting.txt")))
                .param("analyze", "true")).andExpect(status().is3xxRedirection()).andReturn();
        String postingUrl = added.getResponse().getRedirectedUrl();
        Posting posting = facade.postings().stream().filter(Posting::isAnalyzed).reduce((a, b) -> b).orElseThrow();
        assertEquals("/postings/" + posting.getId(), postingUrl);

        mvc.perform(get(postingUrl)).andExpect(status().isOk())
                .andExpect(content().string(containsString("100/100")))
                .andExpect(content().string(containsString("Keyword scan")))
                .andExpect(content().string(containsString("Tailor with the agent")));
        mvc.perform(get("/postings")).andExpect(content().string(containsString("Northwind Labs")));

        mvc.perform(get("/resumes")).andExpect(status().isOk()).andExpect(content().string(containsString("CV_Web")));
        mvc.perform(get("/resumes/" + resume.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Ask for a change")));
        mvc.perform(get("/resumes/" + resume.getId() + "/download"))
                .andExpect(header().string("Content-Disposition", containsString("CV_Web.tex")))
                .andExpect(content().string(containsString("\\section{Experience}")));

        String tutor = resume.getBlocks().stream().filter(b -> b.getTitle().equals("Peer Tutor")).findFirst().orElseThrow().getId();
        ((MockLlmClient) llm).enqueueText("{\"rewrite\":[{\"block_id\":\"" + tutor + "\",\"index\":1,\"text\":\"Wrote Java practice sets\"}],\"summary\":\"Java focus\"}");
        mvc.perform(post("/resumes/" + resume.getId() + "/revise").param("instruction", "focus on Java"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/resumes/" + resume.getId())).andExpect(content().string(containsString("Java focus")))
                .andExpect(content().string(containsString("Wrote Java practice sets")));
        mvc.perform(post("/resumes/" + resume.getId() + "/undo"))
                .andExpect(flash().attribute("message", containsString("Undid")));
        assertTrue(facade.resume(resume.getId()).getBlocks().stream().flatMap(b -> b.getBullets().stream())
                .noneMatch(t -> t.equals("Wrote Java practice sets")), "undo restored the original bullet");
        mvc.perform(get("/resumes/" + resume.getId())).andExpect(content().string(containsString("Undid")));

        // manual bullet edit, remove an entry, then the diff and the keyword chips on the resume page
        mvc.perform(post("/resumes/" + resume.getId() + "/bullet").param("blockId", tutor).param("index", "0")
                .param("text", "Tutored first-year students in Java weekly")).andExpect(status().is3xxRedirection());
        String cashier = resume.getBlocks().stream().filter(b -> b.getTitle().equals("Cashier")).findFirst().orElseThrow().getId();
        mvc.perform(post("/resumes/" + resume.getId() + "/remove-block").param("blockId", cashier));
        mvc.perform(get("/resumes/" + resume.getId()))
                .andExpect(content().string(containsString("What changed")))
                .andExpect(content().string(containsString("BLOCK_REMOVED")))
                .andExpect(content().string(containsString("Keywords (F04)")))
                .andExpect(content().string(containsString("Preview")));
        mvc.perform(post("/resumes/" + resume.getId() + "/undo"));
        mvc.perform(post("/resumes/" + resume.getId() + "/bullet").param("blockId", tutor).param("index", "0").param("text", " "))
                .andExpect(flash().attribute("error", containsString("cannot be empty")));

        ((MockLlmClient) llm).enqueueText(CoverLetterTestSupport.letter("Northwind Labs"));
        MvcResult letter = mvc.perform(post("/resumes/" + resume.getId() + "/letter").param("postingId", posting.getId()))
                .andExpect(status().is3xxRedirection()).andReturn();
        mvc.perform(get(letter.getResponse().getRedirectedUrl())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Dear Hiring Manager")));

        mvc.perform(post("/resumes/" + resume.getId() + "/log").param("postingId", posting.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", containsString("Logged Northwind Labs")));
        Application app = appRepo.findAll().get(0);
        assertEquals("CV_Web", app.getResumeName());
        mvc.perform(post("/tracker/" + app.getId() + "/status").param("status", "APPLIED"));
        mvc.perform(get("/tracker")).andExpect(status().isOk()).andExpect(content().string(containsString("APPLIED")));
        mvc.perform(post("/tracker/" + app.getId() + "/status").param("status", "OFFER").param("note", "skip ahead"));

        mvc.perform(post("/dashboard/goals").param("daily", "1").param("weekly", "5"));
        mvc.perform(get("/dashboard")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Goal met")))
                .andExpect(content().string(containsString("Last 7 days")))
                .andExpect(content().string(containsString("Follow-ups due")));
    }

    @Test
    void unknownIdsShowAFriendlyError() throws Exception {
        mvc.perform(get("/postings/does-not-exist")).andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("No posting with id")));
        mvc.perform(get("/resumes/nope")).andExpect(status().isBadRequest());
    }

    @Test
    void illegalStatusMoveIsShownNotSaved() throws Exception {
        Application a = facade.logApplication(null, "Acme", "Dev", "");
        mvc.perform(post("/tracker/" + a.getId() + "/status").param("status", "INTERVIEW"))
                .andExpect(status().is3xxRedirection());
        assertEquals(ca.yorku.cooppilot.tracking.Status.TO_APPLY, facade.application(a.getId()).getStatus());
    }
}
