package com.platform.messaging.web;

import com.platform.common.http.DependencyUnavailableException;
import com.platform.messaging.correlation.CorrelationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformWebHardeningTest {

    @AfterEach
    void clear() {
        CorrelationContext.clear();
    }

    @Test
    void unexpectedErrorsNeverLeakInternals() {
        CorrelationContext.set("cid-123");
        ResponseEntity<Map<String, String>> r = PlatformErrors.unexpected(
                new IllegalStateException("org.postgresql.util.PSQLException: relation secret_table does not exist"));
        assertEquals(500, r.getStatusCode().value());
        Map<String, String> body = r.getBody();
        assertNotNull(body);
        assertTrue(body.get("error").contains("cid-123"));
        assertEquals("cid-123", body.get("correlationId"));
        assertFalse(body.toString().contains("PSQLException"));
        assertFalse(body.toString().contains("secret_table"));
        assertFalse(body.containsKey("cause"));
    }

    @Test
    void saturationAndDependencyFailuresAre503WithRetryAfter() {
        PlatformErrorAdvice advice = new PlatformErrorAdvice();
        ResponseEntity<Map<String, String>> busy = advice.overloaded(new CannotGetJdbcConnectionException("pool"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, busy.getStatusCode());
        assertEquals("2", busy.getHeaders().getFirst("Retry-After"));
        assertEquals("SERVICE_BUSY", busy.getBody().get("code"));

        ResponseEntity<Map<String, String>> dep = advice.dependency(new DependencyUnavailableException("People down"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, dep.getStatusCode());
        assertFalse(dep.getBody().toString().contains("People down"), "dependency detail stays in the log");
    }

    @Test
    void correlationIdIsMirroredIntoLoggingMdc() {
        CorrelationContext.set("abc");
        assertEquals("abc", MDC.get(CorrelationContext.MDC_KEY));
        CorrelationContext.clear();
        assertNull(MDC.get(CorrelationContext.MDC_KEY));
    }

    @Test
    void actuatorHealthIsOpenButMetricsNeedKey() throws Exception {
        ActuatorAccessFilter keyed = new ActuatorAccessFilter("metrics-secret");
        assertEquals(200, run(keyed, "/actuator/health/readiness", null, "10.0.0.5"));
        assertEquals(404, run(keyed, "/actuator/prometheus", null, "10.0.0.5"));
        assertEquals(404, run(keyed, "/actuator/prometheus", "wrong", "127.0.0.1"), "loopback is not enough once a key is set");
        assertEquals(200, run(keyed, "/actuator/prometheus", "metrics-secret", "10.0.0.5"));

        ActuatorAccessFilter local = new ActuatorAccessFilter("");
        assertEquals(200, run(local, "/actuator/metrics", null, "127.0.0.1"));
        assertEquals(404, run(local, "/actuator/metrics", null, "10.0.0.5"));
        assertEquals(200, run(local, "/api/anything", null, "10.0.0.5"), "non-actuator paths untouched");
    }

    private static int run(ActuatorAccessFilter f, String path, String key, String remote) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
        req.setRemoteAddr(remote);
        if (key != null) {
            req.addHeader(ActuatorAccessFilter.METRICS_KEY_HEADER, key);
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }
}
