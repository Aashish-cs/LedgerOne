package com.ledgerone.dto;

import java.time.Instant;
import java.util.List;

public final class SystemDtos {
    private SystemDtos() {}

    public record SystemStatusResponse(
            String application,
            String environment,
            String apiMode,
            boolean marketSimulatorEnabled,
            Instant serverTime,
            List<String> capabilities) {}
}
