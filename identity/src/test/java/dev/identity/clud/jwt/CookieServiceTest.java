package dev.identity.clud.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import dev.identity.clud.session.RefreshCookieService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class RefreshCookieServiceTest {

    private RefreshCookieService cookieService;

    @BeforeEach
    void setUp() {
        cookieService = new RefreshCookieService();
        ReflectionTestUtils.setField(cookieService, "secure", false);
        ReflectionTestUtils.setField(cookieService, "sameSite", "Lax");
        ReflectionTestUtils.setField(cookieService, "path", "/api/identity/auth");
    }

    @Test
    void usesPublicGatewayPathWhenAddingCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieService.addTokenCookie(response, RefreshCookieService.REFRESH_TOKEN_COOKIE, "token", 60_000);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("Path=/api/identity/auth", "HttpOnly");
    }

    @Test
    void usesSamePublicGatewayPathWhenDeletingCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieService.deleteCookie(response, RefreshCookieService.REFRESH_TOKEN_COOKIE);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("Path=/api/identity/auth", "Max-Age=0", "HttpOnly");
    }
}
