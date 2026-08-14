package com.example.smartcanteen.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Small signed JWT implementation for the local backend boundary. */
@Component
public class TokenService {

    private static final String HMAC = "HmacSHA256";
    private static final long ACCESS_SECONDS = 3_600;
    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final SecureRandom random = new SecureRandom();

    public TokenService(
            ObjectMapper objectMapper,
            @Value("${smart-canteen.security.jwt-secret}") String configuredSecret) {
        if (configuredSecret == null || configuredSecret.length() < 32) {
            throw new IllegalArgumentException(
                    "smart-canteen.security.jwt-secret must contain at least 32 characters");
        }
        this.objectMapper = objectMapper;
        this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
    }

    public IssuedAccessToken issueAccess(UserAccount account) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(ACCESS_SECONDS);
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", account.userId());
        claims.put("username", account.username());
        claims.put("displayName", account.displayName());
        claims.put("role", account.role().name());
        claims.put("schoolId", account.schoolId());
        claims.put("canteenId", account.canteenId());
        claims.put("iat", issuedAt.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        claims.put("jti", randomId());
        String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
        String payload = encodeJson(claims);
        String unsigned = header + "." + payload;
        return new IssuedAccessToken(unsigned + "." + encode(sign(unsigned)), ACCESS_SECONDS);
    }

    public String newRefreshToken() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashRefreshToken(String refreshToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public AuthPrincipal verifyAccess(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("access token is required");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("invalid access token");
        }
        byte[] expected = sign(parts[0] + "." + parts[1]);
        byte[] received = decode(parts[2]);
        if (!MessageDigest.isEqual(expected, received)) {
            throw new IllegalArgumentException("invalid access token signature");
        }
        try {
            JsonNode claims = objectMapper.readTree(new String(decode(parts[1]), StandardCharsets.UTF_8));
            long expiresAt = claims.path("exp").asLong(0);
            if (expiresAt <= Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("access token expired");
            }
            return new AuthPrincipal(
                    requiredClaim(claims, "sub"),
                    requiredClaim(claims, "username"),
                    requiredClaim(claims, "displayName"),
                    Role.valueOf(requiredClaim(claims, "role")),
                    nullableClaim(claims, "schoolId"),
                    nullableClaim(claims, "canteenId"));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid access token", exception);
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC signing is unavailable", exception);
        }
    }

    private String encodeJson(Object value) {
        try {
            return encode(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode access token", exception);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private String randomId() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return encode(bytes);
    }

    private static String requiredClaim(JsonNode claims, String name) {
        String value = claims.path(name).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing claim: " + name);
        }
        return value;
    }

    private static String nullableClaim(JsonNode claims, String name) {
        JsonNode value = claims.get(name);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    public record IssuedAccessToken(String value, long expiresIn) {
    }
}
