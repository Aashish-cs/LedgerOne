package com.ledgerone.controller;

import com.ledgerone.dto.ApiResponse;
import com.ledgerone.dto.SystemDtos;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final Environment environment;
    private final boolean marketSimulatorEnabled;

    public SystemController(
            Environment environment,
            @Value("${ledgerone.market.simulator-enabled:true}") boolean marketSimulatorEnabled) {
        this.environment = environment;
        this.marketSimulatorEnabled = marketSimulatorEnabled;
    }

    @GetMapping("/status")
    ApiResponse<SystemDtos.SystemStatusResponse> status() {
        String[] profiles = environment.getActiveProfiles();
        String activeProfile = profiles.length == 0 ? "default" : String.join(",", profiles);
        return ApiResponse.ok(
                "LedgerOne API is online",
                new SystemDtos.SystemStatusResponse(
                        "LedgerOne",
                        activeProfile,
                        "SELF_HOSTED_FREE_API",
                        marketSimulatorEnabled,
                        Instant.now(),
                        List.of(
                                "JWT authentication",
                                "Paper trading",
                                "Buying power checks",
                                "Account analytics",
                                "Live stock quotes",
                                "Risk scoring",
                                "Audit logging")));
    }
}
