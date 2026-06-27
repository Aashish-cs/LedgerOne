package com.ledgerone.dto;

import com.ledgerone.entity.AuditAction;
import java.time.Instant;
import java.util.UUID;

public final class AuditDtos {
    private AuditDtos() {}

    public record AuditLogResponse(
            UUID id,
            UUID userId,
            String userEmail,
            AuditAction action,
            String subject,
            String details,
            String ipAddress,
            Instant createdAt) {}
}
