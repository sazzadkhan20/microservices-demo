package com.platform.registry.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Relative server "/" keeps Swagger Try-it-out on the SAME origin as the UI
 * (avoids localhost vs 127.0.0.1 CORS failures when using the Gateway Swagger).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI registryOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tenant Registry API")
                        .description("""
                                Open Swagger on the Gateway: http://127.0.0.1:8090/swagger-ui.html
                                (use the same host you typed in the browser — do not mix localhost and 127.0.0.1).
                                
                                First call: POST /api/registry/token with tenantId=techshop_retail
                                """)
                        .version("1.0"))
                .servers(List.of(
                        new Server().url("/").description("Same origin as Swagger UI (Gateway)")));
    }
}
