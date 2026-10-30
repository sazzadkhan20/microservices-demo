package com.platform.gateway.filter;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.JwtSupport;
import com.platform.gateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTenantGatewayFilterTest {

    private static final String SECRET = "unit-test-jwt-secret-0123456789abcdef";
    private static final String SERVICE_KEY = "svc-key";
    private static final String DEMO_KEY = "demo-key";

    private JwtSupport jwt;
    private GatewayProperties props;
    private JwtTenantGatewayFilter filter;
    private final AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = (ServerWebExchange ex) -> {
        forwarded.set(ex.getRequest());
        return Mono.empty();
    };

    @BeforeEach
    void setUp() {
        jwt = new JwtSupport(SECRET);
        props = new GatewayProperties();
        props.getSecurity().setServiceKey(SERVICE_KEY);
        props.getDemoAuth().setEnabled(true);
        props.getDemoAuth().setKey(DEMO_KEY);
        props.setPublicPaths(java.util.List.of("/actuator/**", "/docs/*/v3/api-docs", "/docs/*/v3/api-docs/**"));
        filter = new JwtTenantGatewayFilter(jwt, props);
    }

    private MockServerWebExchange run(MockServerHttpRequest.BaseBuilder<?> req) {
        MockServerWebExchange ex = MockServerWebExchange.from(req.build());
        filter.filter(ex, chain).block();
        return ex;
    }

    @Test
    void tokenMintWithoutDemoKeyIs401() {
        var ex = run(MockServerHttpRequest.post("/api/registry/token"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        assertNull(forwarded.get());
    }

    @Test
    void tokenMintWithWrongDemoKeyIs401() {
        var ex = run(MockServerHttpRequest.post("/api/registry/token").header(PlatformHeaders.DEMO_AUTH_KEY, "nope"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
    }

    @Test
    void tokenMintWithDemoKeyForwardsWithServiceKeyAndNoClientSpoofs() {
        run(MockServerHttpRequest.post("/api/registry/token")
                .header(PlatformHeaders.DEMO_AUTH_KEY, DEMO_KEY)
                .header(PlatformHeaders.SERVICE_KEY, "spoofed")
                .header(PlatformHeaders.TENANT_ID, "evil"));
        ServerHttpRequest r = forwarded.get();
        assertEquals(SERVICE_KEY, r.getHeaders().getFirst(PlatformHeaders.SERVICE_KEY));
        assertNull(r.getHeaders().getFirst(PlatformHeaders.TENANT_ID));
        assertNull(r.getHeaders().getFirst(PlatformHeaders.DEMO_AUTH_KEY));
    }

    @Test
    void accountsWithoutKeyIs401AndWithKeyBindsTenantFromQuery() {
        var denied = run(MockServerHttpRequest.get("/hrms/api/auth/accounts?tenantId=qii_creative"));
        assertEquals(HttpStatus.UNAUTHORIZED, denied.getResponse().getStatusCode());
        assertNull(forwarded.get());

        run(MockServerHttpRequest.get("/hrms/api/auth/accounts?tenantId=qii_creative")
                .header(PlatformHeaders.DEMO_AUTH_KEY, DEMO_KEY));
        assertEquals("qii_creative", forwarded.get().getHeaders().getFirst(PlatformHeaders.TENANT_ID));
        assertEquals(SERVICE_KEY, forwarded.get().getHeaders().getFirst(PlatformHeaders.SERVICE_KEY));
    }

    @Test
    void demoAuthDisabledHidesEndpoints() {
        props.getDemoAuth().setEnabled(false);
        var ex = run(MockServerHttpRequest.post("/api/registry/token").header(PlatformHeaders.DEMO_AUTH_KEY, DEMO_KEY));
        assertEquals(HttpStatus.NOT_FOUND, ex.getResponse().getStatusCode());
    }

    @Test
    void jwtRequestGetsTrustedHeadersAndClientSpoofsAreReplaced() {
        String token = jwt.issueToken("e-7", "qii_creative", 7L, 60);
        run(MockServerHttpRequest.get("/payroll/api/payroll/payslips")
                .header("Authorization", "Bearer " + token)
                .header(PlatformHeaders.TENANT_ID, "evil")
                .header(PlatformHeaders.EMPLOYEE_ID, "1")
                .header(PlatformHeaders.SERVICE_KEY, "spoofed"));
        ServerHttpRequest r = forwarded.get();
        assertEquals("qii_creative", r.getHeaders().getFirst(PlatformHeaders.TENANT_ID));
        assertEquals("7", r.getHeaders().getFirst(PlatformHeaders.EMPLOYEE_ID));
        assertEquals(SERVICE_KEY, r.getHeaders().getFirst(PlatformHeaders.SERVICE_KEY));
        assertEquals(1, r.getHeaders().get(PlatformHeaders.SERVICE_KEY).size());
    }

    @Test
    void noJwtIs401() {
        var ex = run(MockServerHttpRequest.get("/payroll/api/payroll/payslips")
                .header(PlatformHeaders.SERVICE_KEY, SERVICE_KEY));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
    }

    @Test
    void internalRegistryRoutesAreNeverProxied() {
        String token = jwt.issueToken("e-7", "qii_creative", 7L, 60);
        var ex = run(MockServerHttpRequest.get("/api/registry/internal/routing/hrms/qii_creative")
                .header("Authorization", "Bearer " + token));
        assertEquals(HttpStatus.NOT_FOUND, ex.getResponse().getStatusCode());
        // also via the public docs prefix
        var ex2 = run(MockServerHttpRequest.get("/docs/registry/api/registry/internal/routing/hrms/x"));
        assertEquals(HttpStatus.NOT_FOUND, ex2.getResponse().getStatusCode());
        assertNull(forwarded.get());
    }

    @Test
    void docsOnlyPublicForOpenApiJsonAndGetNoServiceKey() {
        run(MockServerHttpRequest.get("/docs/hrms/v3/api-docs").header(PlatformHeaders.SERVICE_KEY, "spoofed"));
        assertNull(forwarded.get().getHeaders().getFirst(PlatformHeaders.SERVICE_KEY));

        forwarded.set(null);
        var ex = run(MockServerHttpRequest.get("/docs/registry/api/registry/routing/hrms/x"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        assertNull(forwarded.get());
    }

    @Test
    void pathTraversalRejected() {
        var ex = run(MockServerHttpRequest.method(org.springframework.http.HttpMethod.GET,
                java.net.URI.create("/hrms/api/auth/accounts/%2e%2e/me")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getResponse().getStatusCode());
        assertTrue(forwarded.get() == null);
    }
}
