package dev.gateway.clud.security;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.cors")
public class GatewayCorsProperties {

    @NotEmpty(message = "CORS allowed origins must not be empty")
    private List<String> allowedOrigins = List.of("http://localhost:3000");
}
