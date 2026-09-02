package dev.gateway.clud.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI gatewayOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Clud Gateway API")
                .version("v1")
                .description("API routing and authentication boundary for Clud."));
    }
}
