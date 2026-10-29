package com.platform.registry.api.dto;

/**
 * JWT response. {@code accessToken} is canonical; {@code token} aliases it for frontend clients.
 */
public record TokenResponse(
        String accessToken,
        String token,
        String tokenType,
        String tenantId,
        Long employeeId,
        long expiresInSeconds
) {
    public static TokenResponse of(String jwt, String tenantId, Long employeeId, long ttl) {
        return new TokenResponse(jwt, jwt, "Bearer", tenantId, employeeId, ttl);
    }
}
