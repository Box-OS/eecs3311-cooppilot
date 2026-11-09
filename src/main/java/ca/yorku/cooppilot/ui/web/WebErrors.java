package ca.yorku.cooppilot.ui.web;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Unknown ids and illegal actions show a friendly page instead of a stack trace. */
@ControllerAdvice
public class WebErrors {

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String badRequest(RuntimeException e, Model m) {
        m.addAttribute("message", e.getMessage());
        return "error-page";
    }
}
