package dev.identity.clud.event;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("clud.identity")
public record IdentityEventProperties(
        @NotBlank(message = "Identity events topic must not be blank") String eventsTopic) {
}
