package com.platform.common.http;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Guide §7 "Deadlines": every synchronous service-to-service client gets explicit connect and read
 * timeouts (no unbounded waits that park request threads when a dependency is slow).
 * Defaults: connect 3 s, read 10 s; override with {@code PLATFORM_HTTP_CONNECT_TIMEOUT_MS} /
 * {@code PLATFORM_HTTP_READ_TIMEOUT_MS}.
 */
public final class ResilientHttp {

    private ResilientHttp() {
    }

    public static Duration connectTimeout() {
        return Duration.ofMillis(envLong("PLATFORM_HTTP_CONNECT_TIMEOUT_MS", 3_000));
    }

    public static Duration readTimeout() {
        return Duration.ofMillis(envLong("PLATFORM_HTTP_READ_TIMEOUT_MS", 10_000));
    }

    /** New builder (callers mutate builders — never share one instance between clients). */
    public static RestClient.Builder restClientBuilder() {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout());
        return RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    String cid = currentCorrelationId();
                    if (cid != null && !request.getHeaders().containsKey(CORRELATION_HEADER)) {
                        request.getHeaders().set(CORRELATION_HEADER, cid);
                    }
                    return execution.execute(request, body);
                });
    }

    private static final String CORRELATION_HEADER = com.platform.common.correlation.CorrelationHeaders.HEADER;

    private static String currentCorrelationId() {
        return com.platform.common.correlation.CorrelationHeaders.current();
    }

    private static long envLong(String name, long fallback) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            v = System.getProperty(name);
        }
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            long n = Long.parseLong(v.trim());
            return n > 0 ? n : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
