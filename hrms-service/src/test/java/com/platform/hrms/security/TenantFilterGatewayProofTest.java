package com.platform.hrms.security;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.tenant.TenantContext;
import com.platform.hrms.infrastructure.multitenancy.TenantFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TenantFilterGatewayProofTest {

    private final TenantFilter filter = new TenantFilter(true, "svc-key");

    private MockHttpServletResponse call(MockHttpServletRequest req, AtomicReference<String> seenTenant) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
                seenTenant.set(TenantContext.getTenantId());
            }
        });
        return res;
    }

    @Test
    void spoofedTenantHeaderWithoutServiceKeyIs401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/employees");
        req.addHeader(PlatformHeaders.TENANT_ID, "victim");
        AtomicReference<String> seen = new AtomicReference<>();
        assertEquals(401, call(req, seen).getStatus());
        assertNull(seen.get());
    }

    @Test
    void wrongServiceKeyIs401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/employees");
        req.addHeader(PlatformHeaders.TENANT_ID, "victim");
        req.addHeader(PlatformHeaders.SERVICE_KEY, "guess");
        assertEquals(401, call(req, new AtomicReference<>()).getStatus());
    }

    @Test
    void validServiceKeyBindsTenant() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/employees");
        req.addHeader(PlatformHeaders.TENANT_ID, "qii_creative");
        req.addHeader(PlatformHeaders.SERVICE_KEY, "svc-key");
        AtomicReference<String> seen = new AtomicReference<>();
        assertEquals(200, call(req, seen).getStatus());
        assertEquals("qii_creative", seen.get());
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void accountsQueryParamNoLongerBypassesProof() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/auth/accounts");
        req.setParameter("tenantId", "victim");
        assertEquals(401, call(req, new AtomicReference<>()).getStatus());
    }
}
