package com.ledgerone.dto;

import com.ledgerone.entity.AccountStatus;
import com.ledgerone.entity.RoleName;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class AdminDtos {
    private AdminDtos() {}

    public record UserAdminResponse(
            UUID id,
            String email,
            String fullName,
            AccountStatus status,
            boolean enabled,
            Set<RoleName> roles,
            Instant createdAt,
            Instant updatedAt) {}

    public record AdminActionResponse(UUID userId, AccountStatus status, String message) {}
}
