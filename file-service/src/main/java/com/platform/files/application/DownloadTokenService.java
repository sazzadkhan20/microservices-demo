package com.platform.files.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Short-lived HMAC download tokens (guide §8).
 * Issued only after authorization + RELEASED scan; audited separately from byte access.
 */
@Service
public class DownloadTokenService {

    private final byte[] secret;
    private final long ttlSeconds;

    public DownloadTokenService(
            @Value("${file.download.signing-key:${JWT_SECRET:}}") String signingKey,
            @Value("${file.download.ttl-seconds:120}") long ttlSeconds) {
        if (signingKey == null || signingKey.isBlank()) {
            throw new IllegalStateException("file.download.signing-key (or JWT_SECRET) is required");
        }
        this.secret = signingKey.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = Math.max(30L, Math.min(ttlSeconds, 900L));
    }

    public IssuedToken issue(Long fileId, String tenantId, Long requesterEmployeeId) {
        long exp = Instant.now().getEpochSecond() + ttlSeconds;
        String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String emp = requesterEmployeeId == null ? "0" : String.valueOf(requesterEmployeeId);
        String body = fileId + "|" + tenantId + "|" + exp + "|" + emp + "|" + nonce;
        String sig = hmac(body);
        return new IssuedToken(body + "|" + sig, Instant.ofEpochSecond(exp));
    }

    public Claims verify(String token, Long expectedFileId, String expectedTenantId) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Download token required");
        }
        String[] parts = token.split("\\|");
        if (parts.length != 6) {
            throw new IllegalArgumentException("Invalid download token");
        }
        String body = parts[0] + "|" + parts[1] + "|" + parts[2] + "|" + parts[3] + "|" + parts[4];
        String expectedSig = hmac(body);
        if (!MessageDigest.isEqual(
                expectedSig.getBytes(StandardCharsets.UTF_8),
                parts[5].getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid download token signature");
        }
        long exp = Long.parseLong(parts[2]);
        if (Instant.now().getEpochSecond() > exp) {
            throw new IllegalArgumentException("Download token expired");
        }
        Long fileId = Long.parseLong(parts[0]);
        String tenantId = parts[1];
        if (!expectedFileId.equals(fileId) || !expectedTenantId.equals(tenantId)) {
            throw new IllegalArgumentException("Download token does not match file");
        }
        Long emp = "0".equals(parts[3]) ? null : Long.parseLong(parts[3]);
        return new Claims(fileId, tenantId, Instant.ofEpochSecond(exp), emp);
    }

    private String hmac(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign download token", e);
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {}

    public record Claims(Long fileId, String tenantId, Instant expiresAt, Long requesterEmployeeId) {}
}
