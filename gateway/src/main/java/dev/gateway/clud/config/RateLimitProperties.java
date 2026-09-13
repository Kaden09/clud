package dev.gateway.clud.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        boolean failOpen,
        @Min(1) int defaultLimitPerMinute,
        List<@Valid PathLimit> limits,
        List<String> skippedPaths
) {

    public record PathLimit(@NotBlank String path, @Min(1) int perMinute) {}

    public RateLimitProperties {
        if (limits == null) {
            limits = List.of();
        }
        if (skippedPaths == null) {
            skippedPaths = List.of();
        }
    }
}
