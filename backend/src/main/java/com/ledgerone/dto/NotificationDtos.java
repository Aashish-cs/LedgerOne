package com.ledgerone.dto;

import com.ledgerone.entity.NotificationType;
import java.time.Instant;
import java.util.UUID;

public final class NotificationDtos {
    private NotificationDtos() {}

    public record NotificationResponse(
            UUID id,
            NotificationType type,
            String title,
            String message,
            boolean read,
            Instant createdAt) {}
}
