package com.platform.people.security;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.AuthContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GatewayAuthFilterProofTest {

    private final GatewayAuthFilter filter = new GatewayAuthFilter(true, "svc-key");

    private MockHttpServletResponse call(MockHttpServletRequest req, AtomicReference<Long> seenEmployee) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
                seenEmployee.set(AuthContext.getEmployeeId());
            }
        });
        return res;
    }

    @Test
    void spoofedEmployeeHeaderWithoutServiceKeyIs401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/employees");
        req.addHeader(PlatformHeaders.EMPLOYEE_ID, "1");
        AtomicReference<Long> seen = new AtomicReference<>();
        assertEquals(401, call(req, seen).getStatus());
        assertNull(seen.get());
    }

    @Test
    void wrongServiceKeyIs401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/employees");
        req.addHeader(PlatformHeaders.EMPLOYEE_ID, "1");
        req.addHeader(PlatformHeaders.SERVICE_KEY, "guess");
        assertEquals(401, call(req, new AtomicReference<>()).getStatus());
    }

    @Test
    void validServiceKeyBindsEmployeeAndClearsAfterwards() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/employees");
        req.addHeader(PlatformHeaders.EMPLOYEE_ID, "7");
        req.addHeader(PlatformHeaders.SERVICE_KEY, "svc-key");
        AtomicReference<Long> seen = new AtomicReference<>();
        assertEquals(200, call(req, seen).getStatus());
        assertEquals(7L, seen.get());
        assertNull(AuthContext.getEmployeeId());
    }

    @Test
    void invalidEmployeeHeaderIs400() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/employees");
        req.addHeader(PlatformHeaders.EMPLOYEE_ID, "abc");
        req.addHeader(PlatformHeaders.SERVICE_KEY, "svc-key");
        assertEquals(400, call(req, new AtomicReference<>()).getStatus());
    }

    @Test
    void accountsPathStillRequiresServiceKey() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/auth/accounts");
        assertEquals(401, call(req, new AtomicReference<>()).getStatus());
    }
}
