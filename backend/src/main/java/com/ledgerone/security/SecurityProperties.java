package com.ledgerone.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ledgerone.security")
public record SecurityProperties(
        String jwtSecret, long accessTokenMinutes, long refreshTokenDays, List<String> allowedOrigins) {}
