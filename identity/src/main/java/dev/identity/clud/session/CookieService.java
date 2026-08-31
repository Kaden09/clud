package dev.identity.clud.session;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

@Service
public class CookieService {

    // Refresh-токен всегда HttpOnly: это ограничение безопасности, а не настройка окружения.
    private static final boolean HTTP_ONLY = true;

    @Value("${app.cookie.secure:false}")
    private boolean secure;

    @Value("${app.cookie.same-site:Lax}")
    private String sameSite;

    @Value("${app.cookie.path:/api/identity/auth}")
    private String path;

    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    public void addTokenCookie(HttpServletResponse response, String name, String value, long maxAgeMs) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(HTTP_ONLY)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(Duration.ofMillis(maxAgeMs))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void deleteCookie(HttpServletResponse response, String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(HTTP_ONLY)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
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
                .map(Cookie::getValue)
                .findFirst();
    }
}