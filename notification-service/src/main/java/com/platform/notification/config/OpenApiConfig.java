package com.platform.notification.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Relative "/notifications" server = calls go through Gateway on the same browser origin.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Notification Service API")
                        .description("""
                                In-app notification inbox.
                                Use Gateway Swagger: pick Notification from the dropdown, Authorize with JWT.
                                """)
                        .version("1.0"))
                .servers(List.of(
                        new Server().url("/notifications").description("Via API Gateway (same origin)")))
                .components(new Components()
                        .addSecuritySchemes(
                                "GatewayJWT",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Session token issued by the gateway (POST /api/auth/token)")))
                .addSecurityItem(new SecurityRequirement().addList("GatewayJWT"));
    }
}
