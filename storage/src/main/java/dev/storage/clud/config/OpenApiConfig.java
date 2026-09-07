package dev.storage.clud.config;

import dev.storage.clud.exception.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI storageOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Clud Storage Service API")
                .version("v1")
                .description("Internal binary object storage backed by MinIO."));
    }

    @Bean
    OpenApiCustomizer apiErrorContract() {
        return api -> {
            if (api.getComponents() == null) {
                api.setComponents(new Components());
            }
            ModelConverters.getInstance().read(ApiError.class).forEach(api.getComponents()::addSchemas);
            api.getComponents().getSchemas().get("ApiError")
                    .setRequired(java.util.List.of("timestamp", "status", "code", "message", "path"));
            if (api.getPaths() != null) {
                api.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                    if (!operation.getResponses().containsKey("default")) {
                        operation.getResponses().addApiResponse("default", new ApiResponse()
                                .description("API error; code is the standard HTTP status name. fieldErrors is optional.")
                                .content(new Content().addMediaType("application/json", new MediaType()
                                        .schema(new Schema<>().$ref("#/components/schemas/ApiError")))));
                    }
                }));
            }
        };
    }
}
