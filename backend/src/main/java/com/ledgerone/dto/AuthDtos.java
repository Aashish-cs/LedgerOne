package com.ledgerone.dto;

import com.ledgerone.entity.AccountStatus;
import com.ledgerone.entity.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 80) String password,
            @NotBlank @Size(min = 2, max = 120) String fullName) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record UserPrincipalResponse(
            UUID id,
            String email,
            String fullName,
            AccountStatus status,
            Set<RoleName> roles,
            Instant createdAt) {}

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            Instant expiresAt,
            UserPrincipalResponse user) {}
}
