package com.ledgerone.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerone.entity.RoleName;
import com.ledgerone.entity.UserAccount;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final ObjectMapper objectMapper;
    private final SecurityProperties properties;

    public String generateAccessToken(UserAccount user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.accessTokenMinutes(), ChronoUnit.MINUTES);
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", "ledgerone");
        payload.put("sub", user.getEmail());
        payload.put("uid", user.getId().toString());
        payload.put("name", user.getFullName());
        payload.put("roles", user.getRoles().stream().map(role -> role.getName().name()).sorted().toList());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());
        String unsigned = encode(header) + "." + encode(payload);
        return unsigned + "." + sign(unsigned);
    }

    public Instant accessTokenExpiresAt() {
        return Instant.now().plus(properties.accessTokenMinutes(), ChronoUnit.MINUTES);
    }

    public Optional<JwtClaims> parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !constantTimeEquals(sign(parts[0] + "." + parts[1]), parts[2])) {
                return Optional.empty();
            }
            Map<String, Object> payload = objectMapper.readValue(DECODER.decode(parts[1]), new TypeReference<>() {});
            long expiresAt = Long.parseLong(payload.get("exp").toString());
            if (Instant.ofEpochSecond(expiresAt).isBefore(Instant.now())) {
                return Optional.empty();
            }
            @SuppressWarnings("unchecked")
            List<String> roleValues = (List<String>) payload.getOrDefault("roles", List.of());
            return Optional.of(new JwtClaims(
                    payload.get("sub").toString(),
                    roleValues.stream().map(RoleName::valueOf).collect(Collectors.toSet()),
                    Instant.ofEpochSecond(expiresAt)));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private String encode(Map<String, Object> payload) {
        try {
            return ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encode JWT payload", exception);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            byte[] secret = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign JWT", exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    public record JwtClaims(String subject, java.util.Set<RoleName> roles, Instant expiresAt) {}
}
