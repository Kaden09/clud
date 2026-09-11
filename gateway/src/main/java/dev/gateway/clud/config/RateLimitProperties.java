package dev.gateway.clud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        boolean failOpen,
        int defaultLimitPerMinute,
        List<PathLimit> limits,
        List<String> skippedPaths
) {

    public record PathLimit(String path, int perMinute) {}

    public RateLimitProperties {
        if(limits == null) {
            limits = List.of();
        }
        if(skippedPaths == null) {
            skippedPaths = List.of();
        }
    }
}
