package com.platform.common.security;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HrmsPermissionClientTest {

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> body =
            new AtomicReference<>("{\"permissions\":[\"reports:view\",\"leave:read\"]}");
    private final AtomicReference<String> seenTenant = new AtomicReference<>();
    private final AtomicReference<String> seenEmployee = new AtomicReference<>();
    private final AtomicReference<String> seenKey = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/me", ex -> {
            calls.incrementAndGet();
            seenTenant.set(ex.getRequestHeaders().getFirst("X-Tenant-ID"));
            seenEmployee.set(ex.getRequestHeaders().getFirst("X-Employee-ID"));
            seenKey.set(ex.getRequestHeaders().getFirst("X-Service-Key"));
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status.get(), bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private HrmsPermissionClient client(Duration ttl) {
        return new HrmsPermissionClient("http://127.0.0.1:" + server.getAddress().getPort() + "/", "k", ttl);
    }

    @Test
    void forwardsIdentityAndParsesPermissions() {
        HrmsPermissionClient c = client(Duration.ZERO);
        assertTrue(c.has("t1", 7L, "reports", "view"));
        assertFalse(c.has("t1", 7L, "reports", "export"));
        assertEquals("t1", seenTenant.get());
        assertEquals("7", seenEmployee.get());
        assertEquals("k", seenKey.get());
    }

    @Test
    void viewReadAliasAndWildcards() {
        assertTrue(HrmsPermissionClient.grants(Set.of("leave:read"), "leave", "view"));
        assertTrue(HrmsPermissionClient.grants(Set.of("leave:view"), "leave", "read"));
        assertTrue(HrmsPermissionClient.grants(Set.of("*:*"), "anything", "manage"));
        assertTrue(HrmsPermissionClient.grants(Set.of("notifications:*"), "notifications", "manage"));
        assertFalse(HrmsPermissionClient.grants(Set.of("notifications:view"), "notifications", "manage"));
        assertFalse(HrmsPermissionClient.grants(Set.of(), "a", "b"));
    }

    @Test
    void cachesWithinTtl() {
        HrmsPermissionClient c = client(Duration.ofSeconds(30));
        c.permissions("t1", 7L);
        c.permissions("t1", 7L);
        assertEquals(1, calls.get());
        c.permissions("t1", 8L);
        assertEquals(2, calls.get());
    }

    @Test
    void clientErrorMeansNoPermissions_serverErrorFailsClosed() {
        status.set(400);
        assertTrue(client(Duration.ZERO).permissions("t1", 7L).isEmpty());
        status.set(500);
        assertThrows(PermissionLookupException.class, () -> client(Duration.ZERO).permissions("t1", 7L));
    }

    @Test
    void missingIdentityHasNoPermissions() {
        HrmsPermissionClient c = client(Duration.ZERO);
        assertTrue(c.permissions(null, 7L).isEmpty());
        assertTrue(c.permissions("t1", null).isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void internalKeyIsNotTheGatewayServiceKey() {
        assertTrue(InternalCalls.isTrusted("secret", "secret"));
        assertFalse(InternalCalls.isTrusted("secret", "other"));
        assertFalse(InternalCalls.isTrusted(null, "secret"));
        assertFalse(InternalCalls.isTrusted("", ""));
        assertFalse("X-Service-Key".equalsIgnoreCase(InternalCalls.HEADER));
    }
}
