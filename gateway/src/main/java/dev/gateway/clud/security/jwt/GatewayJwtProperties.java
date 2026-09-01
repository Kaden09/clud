package dev.gateway.clud.security.jwt;

import java.util.Base64;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "jwt")
public class GatewayJwtProperties {

    @NotBlank(message = "JWT secret must not be blank")
    private String secret;

    @AssertTrue(message = "JWT secret must be at least 32 bytes after Base64 decoding")
    public boolean isSecretValid() {
        if (secret == null || secret.isBlank()) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(secret.trim()).length >= 32;
        }
        catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
