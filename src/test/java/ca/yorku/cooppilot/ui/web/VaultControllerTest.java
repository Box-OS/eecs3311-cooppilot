package ca.yorku.cooppilot.ui.web;

import ca.yorku.cooppilot.app.CoopPilotApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CoopPilotApplication.class)
@AutoConfigureMockMvc
class VaultControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void uploadImportsAndPageListsEntries() throws Exception {
        byte[] tex = Files.readAllBytes(Path.of("samples/sample-resume.tex"));
        mvc.perform(multipart("/vault/import").file(new MockMultipartFile("file", "r.tex", "text/plain", tex))
                        .param("name", "CV_Sample"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vault"))
                .andExpect(flash().attribute("message", containsString("CV_Sample")));

        mvc.perform(get("/vault").param("q", "tutor"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Peer Tutor")));
    }

    @Test
    void addEntryFormSavesEntry() throws Exception {
        mvc.perform(post("/vault/entries").param("title", "Hackathon finalist").param("kind", "ACTIVITY")
                        .param("use", "ALL").param("tags", "python, teamwork"))
                .andExpect(redirectedUrl("/vault"));
        mvc.perform(get("/vault"))
                .andExpect(content().string(containsString("Hackathon finalist")));
    }

    @Test
    void emptyUploadShowsError() throws Exception {
        mvc.perform(multipart("/vault/import").file(new MockMultipartFile("file", new byte[0])))
                .andExpect(flash().attribute("error", containsString(".tex")));
    }
}
