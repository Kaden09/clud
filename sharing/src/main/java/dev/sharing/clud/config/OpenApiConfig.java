package dev.sharing.clud.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI sharingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Clud Sharing Service API")
                .version("v1")
                .description("Public share links, metadata, previews and downloads."));
    }
}
