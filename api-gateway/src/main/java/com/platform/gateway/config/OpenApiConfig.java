package com.platform.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway Swagger uses Bearer JWT. Gateway verifies token and injects X-Tenant-ID.
 * Do not set X-Tenant-ID yourself when calling through the gateway.
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
                                1) Select Registry → POST /api/registry/token (or /registry/token)
                                2) Authorize with Bearer <accessToken>
                                3) Select HRMS or Payroll → Try it out
                                
                                Gateway strips client X-Tenant-ID and injects tenant from JWT.
                                """)
                        .version("1.0"))
                .components(new Components().addSecuritySchemes(
                        "GatewayJWT",
                        new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste accessToken from Registry /token (without the word Bearer)")))
                .addSecurityItem(new SecurityRequirement().addList("GatewayJWT"));
    }
}
