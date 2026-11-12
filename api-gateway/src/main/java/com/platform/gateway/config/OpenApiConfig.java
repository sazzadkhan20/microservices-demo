package com.platform.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway Swagger uses Bearer JWT. Gateway verifies the token and injects the trusted X-Employee-ID.
 * Clients can never set X-Employee-ID / X-Service-Key themselves (the gateway strips them).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gatewayOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Gateway")
                        .description("""
                                Sole entry point. Flow:
                                1) Obtain a session token via Google OIDC (BFF) or POST /api/auth/oidc/token (X-Oidc-Bff-Key)
                                2) Authorize with Bearer <accessToken>
                                3) Select a service from the dropdown → Try it out
                                
                                Gateway strips client identity headers and injects the employee from the JWT.
                                """)
                        .version("1.0"))
                .components(new Components().addSecuritySchemes(
                        "GatewayJWT",
                        new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste accessToken from POST /api/auth/token (without the word Bearer)")))
                .addSecurityItem(new SecurityRequirement().addList("GatewayJWT"));
    }
}
