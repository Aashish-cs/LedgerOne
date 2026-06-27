package com.ledgerone.scheduler;

import com.ledgerone.service.MarketDataService;
import com.ledgerone.service.TradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketSimulatorScheduler {
    private final MarketDataService marketDataService;
    private final TradingService tradingService;

    @Value("${ledgerone.market.simulator-enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${MARKET_TICK_DELAY_MS:30000}", initialDelayString = "${MARKET_TICK_INITIAL_DELAY_MS:10000}")
    public void tick() {
        if (!enabled) {
            return;
        }
        marketDataService.simulateTick();
        tradingService.fillEligibleLimitOrders();
    }
}
