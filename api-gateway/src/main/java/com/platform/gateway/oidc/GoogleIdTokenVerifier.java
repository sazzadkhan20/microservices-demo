package com.platform.gateway.oidc;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.platform.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Verifies Google ID tokens via JWKS (iss, aud, signature, alg, exp).
 */
@Component
public class GoogleIdTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifier.class);
    private static final Set<String> ALLOWED_ISSUERS = Set.of(
            "https://accounts.google.com",
            "accounts.google.com");

    private final GatewayProperties properties;
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    public GoogleIdTokenVerifier(GatewayProperties properties) {
        this.properties = properties;
        String jwks = properties.getOidc().getJwksUri();
        if (jwks == null || jwks.isBlank()) {
            jwks = "https://www.googleapis.com/oauth2/v3/certs";
        }
        try {
            JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(new URL(jwks));
            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            JWSKeySelector<SecurityContext> keySelector =
                    new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);
            processor.setJWSKeySelector(keySelector);
            this.jwtProcessor = processor;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize Google JWKS verifier: " + ex.getMessage(), ex);
        }
    }

    public record VerifiedGoogleIdentity(
            String subject,
            String email,
            boolean emailVerified,
            String hd) {
    }

    public VerifiedGoogleIdentity verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("id_token is required");
        }
        GatewayProperties.Oidc oidc = properties.getOidc();
        String clientId = oidc.getGoogleClientId();
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("GOOGLE_CLIENT_ID is not configured");
        }
        try {
            JWTClaimsSet claims = jwtProcessor.process(idToken, null);
            String iss = claims.getIssuer();
            if (iss == null || !ALLOWED_ISSUERS.contains(iss)) {
                throw new IllegalArgumentException("Invalid id_token issuer");
            }
            List<String> audience = claims.getAudience();
            if (audience == null || audience.stream().noneMatch(clientId::equals)) {
                throw new IllegalArgumentException("Invalid id_token audience");
            }
            Date exp = claims.getExpirationTime();
            if (exp == null || exp.toInstant().isBefore(Instant.now().minusSeconds(30))) {
                throw new IllegalArgumentException("id_token expired");
            }
            String sub = claims.getSubject();
            if (sub == null || sub.isBlank()) {
                throw new IllegalArgumentException("id_token missing sub");
            }
            String email = claims.getStringClaim("email");
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("id_token missing email");
            }
            Boolean verified = claims.getBooleanClaim("email_verified");
            boolean emailVerified = Boolean.TRUE.equals(verified)
                    || "true".equalsIgnoreCase(String.valueOf(claims.getClaim("email_verified")));
            String hd = claims.getStringClaim("hd");
            assertAllowedDomain(email, oidc.getAllowedEmailDomains());
            return new VerifiedGoogleIdentity(sub, email.trim().toLowerCase(Locale.ROOT), emailVerified, hd);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Google id_token verification failed: {}", ex.getMessage());
            throw new IllegalArgumentException("Invalid Google id_token");
        }
    }

    static void assertAllowedDomain(String email, String allowedCsv) {
        Set<String> allowed = parseDomains(allowedCsv);
        if (allowed.isEmpty()) {
            return;
        }
        String lower = email.trim().toLowerCase(Locale.ROOT);
        int at = lower.lastIndexOf('@');
        if (at < 0) {
            throw new IllegalArgumentException("Sign-in is limited to company Google accounts.");
        }
        String domain = lower.substring(at + 1);
        if (!allowed.contains(domain)) {
            throw new IllegalArgumentException("Sign-in is limited to company Google accounts.");
        }
    }

    static Set<String> parseDomains(String csv) {
        Set<String> out = new HashSet<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .forEach(out::add);
        return out;
    }
}
