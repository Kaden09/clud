package dev.identity.clud.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

@Service
public class RefreshCookieService {

    // Refresh-токен всегда HttpOnly: это ограничение безопасности, а не настройка окружения.
    private static final boolean HTTP_ONLY = true;
    private static final String SAME_SITE = "Lax";

    private final RefreshCookieProperties properties;

    public RefreshCookieService(RefreshCookieProperties properties) {
        this.properties = properties;
    }

    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    public void addTokenCookie(HttpServletResponse response, String name, String value, long maxAgeMs) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(HTTP_ONLY)
                .secure(properties.secure())
                .sameSite(SAME_SITE)
                .path(properties.path())
                .maxAge(Duration.ofMillis(maxAgeMs))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void deleteCookie(HttpServletResponse response, String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(HTTP_ONLY)
                .secure(properties.secure())
                .sameSite(SAME_SITE)
                .path(properties.path())
                .maxAge(Duration.ZERO)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public Optional<String> getCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> cookie.getName().equals(name))
                .map(cookie -> cookie.getValue())
                .findFirst();
    }
}
