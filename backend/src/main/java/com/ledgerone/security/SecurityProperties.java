package com.ledgerone.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ledgerone.security")
public record SecurityProperties(String jwtSecret, long accessTokenMinutes, long refreshTokenDays) {}
