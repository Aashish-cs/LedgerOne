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
import com.ledgerone.repository.UserAccountRepository;
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
    private UserAccountRepository userRepository;
    @Mock
    private TradeOrderRepository.PendingOrder pendingOrder;

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
                userRepository,
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

        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        TradingDtos.OrderResponse actual = tradingService.placeOrder(user.getId(), orderRequest("duplicate-1"));

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

        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        tradingService.placeOrder(user.getId(), orderRequest("buy-1"));

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

        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        tradingService.placeOrder(user.getId(), orderRequest("buy-2"));

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

    @Test
    void waitingBuyDoesNotDebitCash() {
        var request = new TradingDtos.OrderRequest(portfolio.getId(), "AAPL", OrderSide.BUY,
                OrderType.LIMIT, BigDecimal.ONE, new BigDecimal("90"), "waiting-buy");
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(portfolioService.getOwnedPortfolio(user, portfolio.getId())).thenReturn(portfolio);
        when(marketDataService.findTradableStock("AAPL")).thenReturn(stock);

        tradingService.placeOrder(user.getId(), request);

        var order = ArgumentCaptor.forClass(TradeOrder.class);
        verify(orderRepository).save(order.capture());
        assertThat(order.getValue().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("1000");
        verify(ledgerTransactionRepository, never()).save(any());
    }

    @Test
    void eligibleBuyRechecksBuyingPowerWithoutDebitingWhenCashWasSpent() {
        TradeOrder order = pendingLimit(OrderSide.BUY);
        portfolio.setCashBalance(new BigDecimal("100")); // The $1 execution fee no longer fits.
        when(marketDataService.findTradableStock("AAPL")).thenReturn(stock);

        tradingService.fillPendingOrder(pendingOrder);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getRejectionReason()).isEqualTo("Buying power is insufficient");
        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("100");
        verify(ledgerTransactionRepository, never()).save(any());
    }

    @Test
    void eligibleSellRechecksAvailableShares() {
        TradeOrder order = pendingLimit(OrderSide.SELL);
        when(marketDataService.findTradableStock("AAPL")).thenReturn(stock);
        when(holdingRepository.findByPortfolioAndStock(portfolio, stock)).thenReturn(Optional.empty());

        tradingService.fillPendingOrder(pendingOrder);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getRejectionReason()).isEqualTo("Shares available are insufficient");
        verify(ledgerTransactionRepository, never()).save(any());
    }

    @Test
    void staleScheduledReferenceDoesNotRefillCancelledOrder() {
        TradeOrder order = pendingLimit(OrderSide.BUY);
        order.setStatus(OrderStatus.CANCELLED);

        tradingService.fillPendingOrder(pendingOrder);

        verify(marketDataService, never()).findTradableStock(any());
        verify(ledgerTransactionRepository, never()).save(any());
    }

    private TradeOrder pendingLimit(OrderSide side) {
        TradeOrder order = new TradeOrder();
        order.setId(UUID.randomUUID());
        order.setUser(user);
        order.setPortfolio(portfolio);
        order.setStock(stock);
        order.setSide(side);
        order.setType(OrderType.LIMIT);
        order.setQuantity(BigDecimal.ONE);
        order.setLimitPrice(stock.getLastPrice());
        when(pendingOrder.getUserId()).thenReturn(user.getId());
        when(pendingOrder.getId()).thenReturn(order.getId());
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        return order;
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
