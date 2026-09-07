package dev.gateway.clud.error;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class ApiHttpErrorsTests {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController(), new ApiErrorController())
            .setHandlerExceptionResolvers(new ApiHttpExceptionResolver(mapper)).build();

    @Test
    void preservesRoutingAndBindingStatuses() throws Exception {
        assertError(get("/unknown"), HttpStatus.NOT_FOUND, "/unknown");
        var method = assertError(post("/probe"), HttpStatus.METHOD_NOT_ALLOWED, "/probe");
        assertThat(method.getHeader("Allow")).contains("GET");
        assertError(get("/probe"), HttpStatus.BAD_REQUEST, "/probe");
        assertError(get("/probe").param("number", "not-a-number"), HttpStatus.BAD_REQUEST, "/probe");
        assertError(post("/body").contentType(MediaType.APPLICATION_JSON).content("{"),
                HttpStatus.BAD_REQUEST, "/body");
        assertError(post("/body").contentType(MediaType.TEXT_PLAIN).content("value"),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "/body");
    }

    @Test
    void headDoesNotWriteAnErrorBody() throws Exception {
        var response = mvc.perform(head("/unknown")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    void errorDispatchPreservesOriginalPathAndHidesExceptionDetails() throws Exception {
        var response = assertError(get("/error")
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/original")
                .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("private backend detail")),
                HttpStatus.INTERNAL_SERVER_ERROR, "/original");
        assertThat(response.getContentAsString()).doesNotContain("private backend detail");
        assertError(get("/error"), HttpStatus.NOT_FOUND, "/error");
    }

    private org.springframework.mock.web.MockHttpServletResponse assertError(
            MockHttpServletRequestBuilder request, HttpStatus status, String path) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(status.value());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        var json = mapper.readTree(response.getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(status.value());
        assertThat(json.get("code").asText()).isEqualTo(status.name());
        assertThat(json.get("path").asText()).isEqualTo(path);
        assertThat(json.get("message").asText()).isNotBlank();
        assertThat(json.get("timestamp").asText()).isNotBlank();
        assertThat(json.has("fieldErrors")).isFalse();
        return response;
    }

    @RestController
    static class ProbeController {
        @GetMapping("/probe")
        String probe(@RequestParam("number") int number) { return "ok"; }

        @PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        String body(@RequestBody java.util.Map<String, String> body) { return "ok"; }
    }
}
