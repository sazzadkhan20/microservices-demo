package com.platform.hrms.config;

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
 * Relative "/hrms" server = calls go through Gateway on the same browser origin.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI hrmsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("HRMS Service API")
                        .description("""
                                Use Gateway Swagger: pick HRMS from the dropdown, Authorize with JWT.
                                Do not mix localhost and 127.0.0.1 in the browser address bar.
                                """)
                        .version("1.0"))
                .servers(List.of(
                        new Server().url("/hrms").description("Via API Gateway (same origin)")))
                .components(new Components()
                        .addSecuritySchemes(
                                "GatewayJWT",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Token from Registry POST /api/registry/token"))
                        .addSecuritySchemes(
                                "TenantHeader",
                                new SecurityScheme()
                                        .name("X-Tenant-ID")
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .description("Direct :8081 debug only")))
                .addSecurityItem(new SecurityRequirement().addList("GatewayJWT"))
                .addSecurityItem(new SecurityRequirement().addList("TenantHeader"));
    }
}
