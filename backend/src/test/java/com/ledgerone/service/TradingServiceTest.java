package com.ledgerone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ledgerone.audit.AuditService;
import com.ledgerone.dto.TradingDtos;
import com.ledgerone.entity.Holding;
import com.ledgerone.entity.LedgerTransaction;
import com.ledgerone.entity.OrderSide;
import com.ledgerone.entity.OrderStatus;
import com.ledgerone.entity.OrderType;
import com.ledgerone.entity.Portfolio;
import com.ledgerone.entity.Stock;
import com.ledgerone.entity.TradeOrder;
import com.ledgerone.entity.UserAccount;
import com.ledgerone.mapper.TradeOrderMapper;
import com.ledgerone.mapper.TransactionMapper;
import com.ledgerone.notification.NotificationService;
import com.ledgerone.repository.HoldingRepository;
import com.ledgerone.repository.LedgerTransactionRepository;
import com.ledgerone.repository.TradeOrderRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradingServiceTest {
    @Mock
    private TradeOrderRepository orderRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private MarketDataService marketDataService;

    @Mock
    private TradeOrderMapper tradeOrderMapper;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private AuditService auditService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private RiskService riskService;

    private TradingService tradingService;
    private UserAccount user;
    private Portfolio portfolio;
    private Stock stock;

    @BeforeEach
    void setUp() {
        tradingService = new TradingService(
                orderRepository,
                holdingRepository,
                ledgerTransactionRepository,
                portfolioService,
                marketDataService,
                new TradingProperties(new BigDecimal("0.0010"), new BigDecimal("1.00"), new BigDecimal("50.00")),
                tradeOrderMapper,
                transactionMapper,
                auditService,
                notificationService,
                riskService);
        user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("user@ledgerone.com");
        user.setFullName("Ashish Mishra");

        portfolio = new Portfolio();
        portfolio.setId(UUID.randomUUID());
        portfolio.setUser(user);
        portfolio.setName("Core Growth Portfolio");
        portfolio.setCashBalance(new BigDecimal("1000.0000"));

        stock = new Stock();
        stock.setId(UUID.randomUUID());
        stock.setSymbol("AAPL");
        stock.setCompanyName("Apple Inc.");
        stock.setSector("Technology");
        stock.setLastPrice(new BigDecimal("100.0000"));
    }

    @Test
    void duplicateClientOrderIdReturnsExistingOrderWithoutCreatingAnotherOrder() {
        TradeOrder existing = new TradeOrder();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setPortfolio(portfolio);
        existing.setStock(stock);
        existing.setClientOrderId("duplicate-1");
        existing.setStatus(OrderStatus.FILLED);
        TradingDtos.OrderResponse expected = orderResponse(existing.getId(), "duplicate-1", OrderStatus.FILLED);

        when(orderRepository.findByUserAndClientOrderId(user, "duplicate-1")).thenReturn(Optional.of(existing));
        when(tradeOrderMapper.toResponse(existing)).thenReturn(expected);

        TradingDtos.OrderResponse actual = tradingService.placeOrder(user, orderRequest("duplicate-1"));

        assertThat(actual).isEqualTo(expected);
        verify(orderRepository, never()).save(any());
        verify(portfolioService, never()).getOwnedPortfolio(any(), any());
    }

    @Test
    void rejectedBuyOrderIsPersistedWithReasonWhenBuyingPowerIsInsufficient() {
        portfolio.setCashBalance(new BigDecimal("50.0000"));
        when(orderRepository.findByUserAndClientOrderId(user, "buy-1")).thenReturn(Optional.empty());
        when(portfolioService.getOwnedPortfolio(user, portfolio.getId())).thenReturn(portfolio);
        when(marketDataService.findTradableStock("AAPL")).thenReturn(stock);
        when(orderRepository.save(any(TradeOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeOrderMapper.toResponse(any(TradeOrder.class)))
                .thenReturn(orderResponse(UUID.randomUUID(), "buy-1", OrderStatus.REJECTED));

        tradingService.placeOrder(user, orderRequest("buy-1"));

        ArgumentCaptor<TradeOrder> orderCaptor = ArgumentCaptor.forClass(TradeOrder.class);
        verify(orderRepository).save(orderCaptor.capture());
        TradeOrder rejected = orderCaptor.getValue();
        assertThat(rejected.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Buying power is insufficient");
        verify(ledgerTransactionRepository, never()).save(any());
        verify(riskService, never()).evaluateAndPersist(any());
    }

    @Test
    void filledMarketBuyUpdatesCashHoldingLedgerAndRisk() {
        when(orderRepository.findByUserAndClientOrderId(user, "buy-2")).thenReturn(Optional.empty());
        when(portfolioService.getOwnedPortfolio(user, portfolio.getId())).thenReturn(portfolio);
        when(marketDataService.findTradableStock("AAPL")).thenReturn(stock);
        when(orderRepository.save(any(TradeOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(holdingRepository.findByPortfolioAndStock(portfolio, stock)).thenReturn(Optional.empty());
        when(tradeOrderMapper.toResponse(any(TradeOrder.class)))
                .thenReturn(orderResponse(UUID.randomUUID(), "buy-2", OrderStatus.FILLED));

        tradingService.placeOrder(user, orderRequest("buy-2"));

        ArgumentCaptor<Holding> holdingCaptor = ArgumentCaptor.forClass(Holding.class);
        ArgumentCaptor<LedgerTransaction> ledgerCaptor = ArgumentCaptor.forClass(LedgerTransaction.class);
        verify(holdingRepository).save(holdingCaptor.capture());
        verify(ledgerTransactionRepository).save(ledgerCaptor.capture());
        verify(riskService).evaluateAndPersist(portfolio);

        Holding holding = holdingCaptor.getValue();
        assertThat(holding.getQuantity()).isEqualByComparingTo("1.000000");
        assertThat(holding.getAverageCost()).isEqualByComparingTo("101.0000");
        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("899.0000");
        assertThat(ledgerCaptor.getValue().getPrice()).isEqualByComparingTo("100.0000");
        assertThat(ledgerCaptor.getValue().getFees()).isEqualByComparingTo("1.0000");
    }

    private TradingDtos.OrderRequest orderRequest(String clientOrderId) {
        return new TradingDtos.OrderRequest(
                portfolio.getId(),
                "AAPL",
                OrderSide.BUY,
                OrderType.MARKET,
                BigDecimal.ONE,
                null,
                clientOrderId);
    }

    private TradingDtos.OrderResponse orderResponse(UUID id, String clientOrderId, OrderStatus status) {
        return new TradingDtos.OrderResponse(
                id,
                clientOrderId,
                portfolio.getId(),
                portfolio.getName(),
                "AAPL",
                OrderSide.BUY,
                OrderType.MARKET,
                status,
                BigDecimal.ONE,
                null,
                status == OrderStatus.FILLED ? stock.getLastPrice() : null,
                BigDecimal.ONE,
                status == OrderStatus.REJECTED ? "Buying power is insufficient" : null,
                null,
                null);
    }
}
