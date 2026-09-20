package dev.identity.clud.config;

import dev.identity.clud.error.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI identityOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Clud Identity Service API")
                        .version("v1")
                        .description("Registration, authentication, refresh sessions and current user identity."))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSecuritySchemes("refreshCookie", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("refresh_token")));
    }

    @Bean
    OpenApiCustomizer apiErrorContract() {
        return api -> {
            Components components = api.getComponents();
            if (components == null) {
                components = new Components();
                api.setComponents(components);
            }
            ModelConverters.getInstance().read(ApiError.class).forEach(components::addSchemas);
            Schema<?> apiErrorSchema = (Schema<?>) components.getSchemas().get("ApiError");
            apiErrorSchema.setRequired(java.util.List.of("code", "message"));
            if (api.getPaths() != null) {
                api.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                    if (!operation.getResponses().containsKey("default")) {
                        operation.getResponses().addApiResponse("default", new ApiResponse()
                                .description("API error response.")
                                .content(new Content().addMediaType("application/json", new MediaType()
                                        .schema(new Schema<>().$ref("#/components/schemas/ApiError")))));
                    }
                }));
            }
        };
    }
}
