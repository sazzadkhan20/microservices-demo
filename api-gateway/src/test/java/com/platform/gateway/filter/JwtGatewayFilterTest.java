package com.platform.gateway.filter;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.InternalCalls;
import com.platform.common.security.JwtSupport;
import com.platform.gateway.config.GatewayProperties;
import com.platform.gateway.session.EmployeeSessionGuard;
import com.platform.gateway.session.SessionRevocationStore;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtGatewayFilterTest {

    private static final String SECRET = "unit-test-jwt-secret-0123456789abcdef";
    private static final String SERVICE_KEY = "svc-key";
    private static final String DEMO_KEY = "demo-key";

    private JwtSupport jwt;
    private GatewayProperties props;
    private SessionRevocationStore revocation;
    private EmployeeSessionGuard guard;
    private JwtGatewayFilter filter;
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
        revocation = mock(SessionRevocationStore.class);
        when(revocation.isRevoked(any(), any(), any())).thenReturn(Mono.just(false));
        guard = mock(EmployeeSessionGuard.class);
        when(guard.status(any())).thenReturn(Mono.just(EmployeeSessionGuard.Status.ACTIVE));
        filter = new JwtGatewayFilter(jwt, props, revocation, guard);
    }

    private MockServerWebExchange run(MockServerHttpRequest.BaseBuilder<?> req) {
        MockServerWebExchange ex = MockServerWebExchange.from(req.build());
        filter.filter(ex, chain).block();
        return ex;
    }

    @Test
    void accountsWithoutDemoKeyIs401AndWithKeyForwardsServiceKeyOnly() {
        var denied = run(MockServerHttpRequest.get("/hrms/api/auth/accounts"));
        assertEquals(HttpStatus.UNAUTHORIZED, denied.getResponse().getStatusCode());
        assertNull(forwarded.get());

        run(MockServerHttpRequest.get("/hrms/api/auth/accounts")
                .header(PlatformHeaders.DEMO_AUTH_KEY, DEMO_KEY)
                .header(PlatformHeaders.EMPLOYEE_ID, "1"));
        ServerHttpRequest r = forwarded.get();
        assertEquals(SERVICE_KEY, r.getHeaders().getFirst(PlatformHeaders.SERVICE_KEY));
        assertNull(r.getHeaders().getFirst(PlatformHeaders.EMPLOYEE_ID));
        assertNull(r.getHeaders().getFirst(PlatformHeaders.DEMO_AUTH_KEY));
    }

    @Test
    void demoAuthDisabledHidesAccountPicker() {
        props.getDemoAuth().setEnabled(false);
        var ex = run(MockServerHttpRequest.get("/hrms/api/auth/accounts").header(PlatformHeaders.DEMO_AUTH_KEY, DEMO_KEY));
        assertEquals(HttpStatus.NOT_FOUND, ex.getResponse().getStatusCode());
    }

    @Test
    void jwtRequestGetsTrustedHeadersAndClientSpoofsAreReplaced() {
        String token = jwt.issueToken("e-7", 7L, 60);
        run(MockServerHttpRequest.get("/payroll/api/payroll/payslips")
                .header("Authorization", "Bearer " + token)
                .header(PlatformHeaders.EMPLOYEE_ID, "1")
                .header(PlatformHeaders.SERVICE_KEY, "spoofed")
                .header(InternalCalls.HEADER, "spoofed-internal"));
        ServerHttpRequest r = forwarded.get();
        assertEquals("7", r.getHeaders().getFirst(PlatformHeaders.EMPLOYEE_ID));
        assertEquals(SERVICE_KEY, r.getHeaders().getFirst(PlatformHeaders.SERVICE_KEY));
        assertEquals(1, r.getHeaders().get(PlatformHeaders.SERVICE_KEY).size());
        assertNull(r.getHeaders().getFirst(InternalCalls.HEADER));
    }

    @Test
    void revokedSessionIs401() {
        when(revocation.isRevoked(any(), any(), any())).thenReturn(Mono.just(true));
        String token = jwt.issueToken("e-7", 7L, 60);
        var ex = run(MockServerHttpRequest.get("/payroll/api/payroll/payslips")
                .header("Authorization", "Bearer " + token));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        assertNull(forwarded.get());
    }

    @Test
    void inactiveEmployeeSessionIs401() {
        when(guard.status(7L)).thenReturn(Mono.just(EmployeeSessionGuard.Status.INACTIVE));
        String token = jwt.issueToken("e-7", 7L, 60);
        var ex = run(MockServerHttpRequest.get("/hrms/api/me").header("Authorization", "Bearer " + token));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        assertNull(forwarded.get());
    }

    @Test
    void unknownEmployeeStatusIs503NotLogout() {
        when(guard.status(7L)).thenReturn(Mono.just(EmployeeSessionGuard.Status.UNKNOWN));
        String token = jwt.issueToken("e-7", 7L, 60);
        var ex = run(MockServerHttpRequest.get("/hrms/api/me").header("Authorization", "Bearer " + token));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getResponse().getStatusCode());
        assertNull(forwarded.get());
    }

    @Test
    void proxiedActuatorPathsAreNeverForwarded() {
        String token = jwt.issueToken("e-7", 7L, 60);
        for (String p : new String[]{"/payroll/actuator/prometheus", "/hrms/actuator/env", "/files/actuator", "/people/x/actuator/heapdump"}) {
            var ex = run(MockServerHttpRequest.get(p).header("Authorization", "Bearer " + token));
            assertEquals(HttpStatus.NOT_FOUND, ex.getResponse().getStatusCode(), p);
        }
        assertNull(forwarded.get());
        org.junit.jupiter.api.Assertions.assertFalse(JwtGatewayFilter.isProxiedActuatorPath("/actuator/health"));
        org.junit.jupiter.api.Assertions.assertFalse(JwtGatewayFilter.isProxiedActuatorPath("/hrms/api/actuatorish"));
    }

    @Test
    void noJwtIs401() {
        var ex = run(MockServerHttpRequest.get("/payroll/api/payroll/payslips")
                .header(PlatformHeaders.SERVICE_KEY, SERVICE_KEY));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
    }

    @Test
    void internalRoutesAreNeverProxied() {
        String token = jwt.issueToken("e-7", 7L, 60);
        var ex = run(MockServerHttpRequest.get("/hrms/api/internal/employees/1/auth-check")
                .header("Authorization", "Bearer " + token));
        assertEquals(HttpStatus.NOT_FOUND, ex.getResponse().getStatusCode());
        var ex2 = run(MockServerHttpRequest.get("/docs/people/api/internal/employees"));
        assertEquals(HttpStatus.NOT_FOUND, ex2.getResponse().getStatusCode());
        assertNull(forwarded.get());
    }

    @Test
    void docsOnlyPublicForOpenApiJsonAndGetNoServiceKey() {
        run(MockServerHttpRequest.get("/docs/people/v3/api-docs").header(PlatformHeaders.SERVICE_KEY, "spoofed"));
        assertNull(forwarded.get().getHeaders().getFirst(PlatformHeaders.SERVICE_KEY));

        forwarded.set(null);
        var ex = run(MockServerHttpRequest.get("/docs/people/api/employees"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getResponse().getStatusCode());
        assertNull(forwarded.get());
    }

    @Test
    void pathTraversalRejected() {
        var ex = run(MockServerHttpRequest.method(org.springframework.http.HttpMethod.GET,
                java.net.URI.create("/hrms/api/auth/accounts/%2e%2e/me")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getResponse().getStatusCode());
        assertNull(forwarded.get());
    }
}
