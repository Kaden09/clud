package dev.identity.clud.jwt;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Base64;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    @NotBlank(message = "JWT secret must not be blank")
    private String secret;

    @NotNull(message = "Access token expiration must be set")
    @Positive(message = "Access token expiration must be positive")
    private long accessTokenExpiration;

    @NotNull(message = "Refresh token expiration must be set")
    @Positive(message = "Refresh token expiration must be positive")
    private long refreshTokenExpiration;

    @AssertTrue(message = "JWT secret must be at least 32 bytes after Base64 decoding")
    public boolean isSecretValid() {
        if (secret == null || secret.isBlank()) {
            return false;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(secret.trim());
            return decoded.length >= 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}