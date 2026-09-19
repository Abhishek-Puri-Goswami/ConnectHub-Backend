package com.connecthub.common.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Client mistakes must be 4xx with the shared body shape; only genuinely unexpected errors are 500. */
class CommonWebExceptionHandlerTest {

    @RestController
    static class Ctl {
        @GetMapping("/needs-user")
        String needsUser(@RequestHeader("X-User-Id") int uid) { return "ok" + uid; }

        @GetMapping("/needs-header")
        String needsHeader(@RequestHeader("X-Other") String v) { return v; }

        @GetMapping("/needs-param")
        String needsParam(@RequestParam int n) { return "n" + n; }

        @PostMapping("/json")
        String json(@RequestBody Map<String, Object> body) { return "ok"; }

        @GetMapping("/boom")
        String boom() { throw new IllegalStateException("secret internal detail"); }
    }

    @org.springframework.web.bind.annotation.RestControllerAdvice
    static class Advice extends CommonWebExceptionHandler {
        @ExceptionHandler(Exception.class)
        ResponseEntity<Map<String, Object>> general(Exception ex) { return internalError(); }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new Ctl()).setControllerAdvice(new Advice()).build();
    }

    @Test
    void missingUserIdHeader_is401() throws Exception {
        mvc.perform(get("/needs-user")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void otherMissingHeader_is400_namingTheHeader() throws Exception {
        mvc.perform(get("/needs-header")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required header: X-Other"));
    }

    @Test
    void missingOrInvalidParameter_is400() throws Exception {
        mvc.perform(get("/needs-param")).andExpect(status().isBadRequest());
        mvc.perform(get("/needs-param").param("n", "abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/needs-user").header("X-User-Id", "not-a-number")).andExpect(status().isBadRequest());
    }

    @Test
    void malformedJson_is400() throws Exception {
        mvc.perform(post("/json").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void wrongMethod_is405_withAllowHeader() throws Exception {
        mvc.perform(delete("/json")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"));
    }

    @Test
    void unsupportedMediaType_is415() throws Exception {
        mvc.perform(post("/json").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void unknownPath_is404() throws Exception {
        mvc.perform(get("/nope")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unexpectedError_is500_withoutLeakingInternals() throws Exception {
        mvc.perform(get("/boom")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }
}
