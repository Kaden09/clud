package dev.gateway.clud.error;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class ApiHttpErrorsTests {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController(), new ApiErrorController())
            .setHandlerExceptionResolvers(new ApiHttpExceptionResolver(mapper)).build();

    @Test
    void preservesRoutingStatuses() throws Exception {
        assertError(get("/unknown"), HttpStatus.NOT_FOUND);
        var method = assertError(post("/probe"), HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(method.getHeader("Allow")).contains("GET");
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
                HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getContentAsString()).doesNotContain("private backend detail");
        assertError(get("/error"), HttpStatus.NOT_FOUND);
    }

    private org.springframework.mock.web.MockHttpServletResponse assertError(
            MockHttpServletRequestBuilder request, HttpStatus status) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(status.value());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        var json = mapper.readTree(response.getContentAsString());
        assertThat(json.get("code").asString()).isEqualTo(status.name());
        assertThat(json.get("message").asString()).isNotBlank();
        assertThat(json.size()).isEqualTo(2);
        return response;
    }

    @RestController
    static class ProbeController {
        @GetMapping("/probe")
        String probe() { return "ok"; }
    }
}
