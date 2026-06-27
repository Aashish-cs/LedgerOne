package com.ledgerone.service;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ledgerone.trading")
public record TradingProperties(BigDecimal feeRate, BigDecimal minFee, BigDecimal maxFee) {}
