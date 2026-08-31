package dev.identity.clud.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI identityOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Clud Identity Service API")
                .version("v1")
                .description("Registration, authentication, refresh sessions and current user identity."));
    }
}
