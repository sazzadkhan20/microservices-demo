package com.platform.gateway.config;

import com.platform.common.constants.PlatformHeaders;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Edge rate limiting (Redis token bucket, {@code RequestRateLimiter} default filter in application.yml).
 *
 * <p>Key = the employee the gateway itself injected after verifying the session (never a client header —
 * {@link com.platform.gateway.filter.JwtGatewayFilter} strips and re-stamps it before route filters run).
 * Anonymous traffic (OpenAPI docs) is keyed by remote address. A human never reaches the default budget
 * (20 req/s sustained, 60 burst); runaway scripts or a compromised session get {@code 429} instead of
 * starving every other user. If Redis is unavailable the limiter fails open (availability first); the
 * session revocation check still fails closed.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public KeyResolver platformRateLimitKeyResolver() {
        return exchange -> {
            String employee = exchange.getRequest().getHeaders().getFirst(PlatformHeaders.EMPLOYEE_ID);
            if (employee != null && !employee.isBlank()) {
                return Mono.just("emp:" + employee.trim());
            }
            InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
            String ip = remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
            return Mono.just("ip:" + ip);
        };
    }
}
