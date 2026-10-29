package com.platform.gateway.filter;

import com.platform.common.constants.PlatformHeaders;
import org.springframework.http.HttpHeaders;

/** Removes every header a client could use to impersonate the gateway. */
final class GatewayHeaders {

    private GatewayHeaders() {
    }

    static void stripTrustHeaders(HttpHeaders headers) {
        headers.remove(PlatformHeaders.TENANT_ID);
        headers.remove(PlatformHeaders.EMPLOYEE_ID);
        headers.remove(PlatformHeaders.SERVICE_KEY);
        headers.remove(PlatformHeaders.DEMO_AUTH_KEY);
    }
}
