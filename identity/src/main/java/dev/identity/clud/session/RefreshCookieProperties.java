package dev.identity.clud.session;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.cookie")
public record RefreshCookieProperties(
        boolean secure,
        @NotBlank(message = "Refresh cookie path must not be blank") String path) {
}
