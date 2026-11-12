package com.platform.common.people;

import com.platform.common.http.DependencyUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Resilience of the People directory client: caching, single-flight, stale-on-error, circuit breaker. */
class PeopleDirectoryClientTest {

    private HttpServer server;
    private final AtomicInteger listCalls = new AtomicInteger();
    private final AtomicInteger empCalls = new AtomicInteger();
    private final AtomicBoolean down = new AtomicBoolean();
    private volatile long delayMs = 0;
    private volatile String lastCorrelationHeader;
    private String base;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/api/internal/employees", ex -> {
            String path = ex.getRequestURI().getPath();
            boolean list = path.equals("/api/internal/employees");
            lastCorrelationHeader = ex.getRequestHeaders().getFirst("X-Correlation-ID");
            (list ? listCalls : empCalls).incrementAndGet();
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            if (down.get()) {
                ex.sendResponseHeaders(503, -1);
                ex.close();
                return;
            }
            if (path.endsWith("/404")) {
                ex.sendResponseHeaders(404, -1);
                ex.close();
                return;
            }
            String body = list ? "[{\"id\":1,\"fullName\":\"A\"},{\"id\":2,\"fullName\":\"B\"}]" : "{\"id\":7,\"fullName\":\"Seven\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private PeopleDirectoryClient client(Duration ttl) {
        return new PeopleDirectoryClient(base, "svc", "int", ttl, Duration.ofSeconds(2));
    }

    @Test
    void listLookupsAreCachedAndCopiesAreIsolated() {
        PeopleDirectoryClient c = client(Duration.ofSeconds(30));
        List<Map<String, Object>> first = c.findAll();
        first.get(0).put("fullName", "mutated by caller");
        first.clear();
        List<Map<String, Object>> second = c.findAll();
        assertEquals(1, listCalls.get(), "second read served from cache");
        assertEquals(2, second.size());
        assertEquals("A", second.get(0).get("fullName"), "callers cannot corrupt the cached rows");
    }

    @Test
    void invalidateEmployeeDropsListsToo() {
        PeopleDirectoryClient c = client(Duration.ofSeconds(30));
        c.findAll();
        c.invalidateEmployee(1L);
        c.findAll();
        assertEquals(2, listCalls.get());
    }

    @Test
    void notFoundIsNotCached() {
        PeopleDirectoryClient c = client(Duration.ofSeconds(30));
        assertTrue(c.findById(404L).isEmpty());
        assertTrue(c.findById(404L).isEmpty());
        assertEquals(2, empCalls.get(), "a missing employee may be created any moment — never cache absence");
    }

    @Test
    void concurrentMissesShareOneCall() throws Exception {
        delayMs = 300;
        PeopleDirectoryClient c = client(Duration.ofSeconds(30));
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return c.findAll().size();
            }));
        }
        go.countDown();
        for (Future<Integer> f : results) {
            assertEquals(2, f.get(5, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        assertEquals(1, listCalls.get(), "no stampede on People");
    }

    @Test
    void staleValueServedWhenPeopleFails() throws Exception {
        PeopleDirectoryClient c = client(Duration.ofMillis(50));
        assertEquals(2, c.findAll().size());
        Thread.sleep(80);
        down.set(true);
        assertEquals(2, c.findAll().size(), "last good value (bounded staleness) instead of an error");
    }

    @Test
    void circuitOpensAfterRepeatedFailuresAndFailsFast() {
        down.set(true);
        PeopleDirectoryClient c = client(Duration.ofSeconds(30));
        for (int i = 0; i < PeopleDirectoryClient.BREAKER_THRESHOLD; i++) {
            assertThrows(DependencyUnavailableException.class, c::findAll);
        }
        int before = listCalls.get();
        long t0 = System.nanoTime();
        assertThrows(DependencyUnavailableException.class, c::findAll);
        assertEquals(before, listCalls.get(), "open circuit does not call People");
        assertTrue(System.nanoTime() - t0 < TimeUnit.MILLISECONDS.toNanos(100), "fails fast");
    }

    @Test
    void forwardsTheCallersCorrelationId() {
        org.slf4j.MDC.put("correlationId", "cid-propagate-1");
        try {
            client(Duration.ZERO).findById(7L);
            assertEquals("cid-propagate-1", lastCorrelationHeader, "guide §9: correlation id crosses HTTP calls");
        } finally {
            org.slf4j.MDC.remove("correlationId");
        }
    }

    @Test
    void failuresSurfaceAsDependencyUnavailable() {
        down.set(true);
        PeopleDirectoryClient c = client(Duration.ZERO);
        DependencyUnavailableException e = assertThrows(DependencyUnavailableException.class, () -> c.findById(7L));
        assertTrue(e instanceof IllegalStateException, "keeps IllegalStateException compatibility for existing callers");
    }
}
