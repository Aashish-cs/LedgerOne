package com.ledgerone.service;

import com.ledgerone.audit.AuditService;
import com.ledgerone.dto.PageResponse;
import com.ledgerone.dto.TradingDtos;
import com.ledgerone.entity.AuditAction;
import com.ledgerone.entity.Holding;
import com.ledgerone.entity.LedgerTransaction;
import com.ledgerone.entity.NotificationType;
import com.ledgerone.entity.OrderSide;
import com.ledgerone.entity.OrderStatus;
import com.ledgerone.entity.OrderType;
import com.ledgerone.entity.Portfolio;
import com.ledgerone.entity.Stock;
import com.ledgerone.entity.TradeOrder;
import com.ledgerone.entity.TransactionAction;
import com.ledgerone.entity.UserAccount;
import com.ledgerone.exception.BadRequestException;
import com.ledgerone.exception.ForbiddenOperationException;
import com.ledgerone.exception.ResourceNotFoundException;
import com.ledgerone.mapper.TradeOrderMapper;
import com.ledgerone.mapper.TransactionMapper;
import com.ledgerone.notification.NotificationService;
import com.ledgerone.repository.HoldingRepository;
import com.ledgerone.repository.LedgerTransactionRepository;
import com.ledgerone.repository.TradeOrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TradingService {
    private final TradeOrderRepository orderRepository;
    private final HoldingRepository holdingRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final PortfolioService portfolioService;
    private final MarketDataService marketDataService;
    private final TradingProperties tradingProperties;
    private final TradeOrderMapper tradeOrderMapper;
    private final TransactionMapper transactionMapper;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final RiskService riskService;

    @Transactional
    public TradingDtos.OrderResponse placeOrder(UserAccount user, TradingDtos.OrderRequest request) {
        if (user.isFrozen()) {
            throw new ForbiddenOperationException("Frozen accounts cannot place orders");
        }
        return orderRepository
                .findByUserAndClientOrderId(user, request.clientOrderId())
                .map(tradeOrderMapper::toResponse)
                .orElseGet(() -> tradeOrderMapper.toResponse(createOrder(user, request)));
    }

    @Transactional(readOnly = true)
    public PageResponse<TradingDtos.OrderResponse> listPortfolioOrders(UserAccount user, UUID portfolioId, Pageable pageable) {
        Portfolio portfolio = portfolioService.getOwnedPortfolio(user, portfolioId);
        return PageResponse.from(orderRepository.findByPortfolio(portfolio, pageable).map(tradeOrderMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<TradingDtos.TransactionResponse> listTransactions(UserAccount user, UUID portfolioId, Pageable pageable) {
        Portfolio portfolio = portfolioService.getOwnedPortfolio(user, portfolioId);
        return PageResponse.from(ledgerTransactionRepository.findByPortfolio(portfolio, pageable).map(transactionMapper::toResponse));
    }

    @Transactional
    public TradingDtos.OrderResponse cancel(UserAccount user, UUID orderId) {
        TradeOrder order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!order.getUser().getId().equals(user.getId())) {
            throw new ForbiddenOperationException("Order does not belong to the authenticated user");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Only pending orders can be cancelled");
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());
        auditService.record(user, AuditAction.ORDER_CANCELLATION, "Order cancelled", order.getClientOrderId());
        return tradeOrderMapper.toResponse(order);
    }

    @Transactional
    public void fillEligibleLimitOrders() {
        orderRepository.findByStatus(OrderStatus.PENDING).forEach(order -> {
            Stock refreshedStock = marketDataService.findTradableStock(order.getStock().getSymbol());
            BigDecimal marketPrice = refreshedStock.getLastPrice();
            boolean executable = order.getSide() == OrderSide.BUY
                    ? order.getLimitPrice().compareTo(marketPrice) >= 0
                    : order.getLimitPrice().compareTo(marketPrice) <= 0;
            if (executable) {
                fill(order, marketPrice);
            }
        });
    }

    private TradeOrder createOrder(UserAccount user, TradingDtos.OrderRequest request) {
        Portfolio portfolio = portfolioService.getOwnedPortfolio(user, request.portfolioId());
        Stock stock = marketDataService.findTradableStock(request.symbol());
        TradeOrder order = new TradeOrder();
        order.setUser(user);
        order.setPortfolio(portfolio);
        order.setStock(stock);
        order.setClientOrderId(request.clientOrderId());
        order.setSide(request.side());
        order.setType(request.type());
        order.setQuantity(Money.quantity(request.quantity()));
        order.setLimitPrice(request.limitPrice() == null ? null : Money.money(request.limitPrice()));

        String validationFailure = validate(order);
        if (validationFailure != null) {
            return reject(order, validationFailure);
        }

        orderRepository.save(order);
        auditService.record(user, AuditAction.ORDER_PLACEMENT, "Order placed", order.getClientOrderId());
        if (order.getType() == OrderType.MARKET || canFillLimit(order, stock.getLastPrice())) {
            fill(order, stock.getLastPrice());
        }
        return order;
    }

    private String validate(TradeOrder order) {
        if (order.getType() == OrderType.LIMIT && order.getLimitPrice() == null) {
            return "Limit orders require a limit price";
        }
        BigDecimal valuationPrice = order.getType() == OrderType.LIMIT ? order.getLimitPrice() : order.getStock().getLastPrice();
        BigDecimal gross = Money.money(valuationPrice.multiply(order.getQuantity()));
        BigDecimal fee = calculateFee(gross);
        order.setFees(fee);
        if (order.getSide() == OrderSide.BUY) {
            BigDecimal requiredCash = Money.money(gross.add(fee));
            if (order.getPortfolio().getCashBalance().compareTo(requiredCash) < 0) {
                return "Buying power is insufficient";
            }
        } else {
            BigDecimal availableShares = holdingRepository
                    .findByPortfolioAndStock(order.getPortfolio(), order.getStock())
                    .map(Holding::getQuantity)
                    .orElse(BigDecimal.ZERO);
            if (availableShares.compareTo(order.getQuantity()) < 0) {
                return "Shares available are insufficient";
            }
        }
        return null;
    }

    private TradeOrder reject(TradeOrder order, String reason) {
        order.setStatus(OrderStatus.REJECTED);
        order.setRejectionReason(reason);
        orderRepository.save(order);
        auditService.record(order.getUser(), AuditAction.ORDER_PLACEMENT, "Order rejected", reason);
        notificationService.create(
                order.getUser(),
                NotificationType.ORDER_REJECTED,
                "Order rejected",
                order.getStock().getSymbol() + " " + order.getSide() + " order rejected: " + reason);
        return order;
    }

    private boolean canFillLimit(TradeOrder order, BigDecimal marketPrice) {
        if (order.getType() != OrderType.LIMIT) {
            return true;
        }
        return order.getSide() == OrderSide.BUY
                ? order.getLimitPrice().compareTo(marketPrice) >= 0
                : order.getLimitPrice().compareTo(marketPrice) <= 0;
    }

    private void fill(TradeOrder order, BigDecimal executionPrice) {
        BigDecimal gross = Money.money(executionPrice.multiply(order.getQuantity()));
        BigDecimal fee = calculateFee(gross);
        order.setExecutionPrice(Money.money(executionPrice));
        order.setFees(fee);
        order.setStatus(OrderStatus.FILLED);
        order.setFilledAt(Instant.now());

        if (order.getSide() == OrderSide.BUY) {
            applyBuy(order, gross, fee);
        } else {
            applySell(order, gross, fee);
        }
        ledgerTransactionRepository.save(toLedgerRecord(order));
        notificationService.create(
                order.getUser(),
                NotificationType.ORDER_FILLED,
                "Order filled",
                order.getStock().getSymbol() + " " + order.getSide() + " order filled at $" + order.getExecutionPrice());
        riskService.evaluateAndPersist(order.getPortfolio());
    }

    private void applyBuy(TradeOrder order, BigDecimal gross, BigDecimal fee) {
        Portfolio portfolio = order.getPortfolio();
        portfolio.setCashBalance(Money.money(portfolio.getCashBalance().subtract(gross).subtract(fee)));
        Holding holding = holdingRepository.findByPortfolioAndStock(portfolio, order.getStock()).orElseGet(() -> {
            Holding created = new Holding();
            created.setPortfolio(portfolio);
            created.setStock(order.getStock());
            return created;
        });
        BigDecimal oldQuantity = holding.getQuantity();
        BigDecimal oldCost = oldQuantity.multiply(holding.getAverageCost());
        BigDecimal newQuantity = oldQuantity.add(order.getQuantity());
        BigDecimal newCost = oldCost.add(gross).add(fee);
        holding.setQuantity(Money.quantity(newQuantity));
        holding.setAverageCost(Money.money(newCost.divide(newQuantity, 4, RoundingMode.HALF_UP)));
        holdingRepository.save(holding);
    }

    private void applySell(TradeOrder order, BigDecimal gross, BigDecimal fee) {
        Holding holding = holdingRepository
                .findByPortfolioAndStock(order.getPortfolio(), order.getStock())
                .orElseThrow(() -> new BadRequestException("Shares available are insufficient"));
        BigDecimal realized = Money.money(order.getExecutionPrice().subtract(holding.getAverageCost()).multiply(order.getQuantity()).subtract(fee));
        holding.setQuantity(Money.quantity(holding.getQuantity().subtract(order.getQuantity())));
        holding.setRealizedProfit(Money.money(holding.getRealizedProfit().add(realized)));
        order.getPortfolio().setCashBalance(Money.money(order.getPortfolio().getCashBalance().add(gross).subtract(fee)));
        order.getPortfolio().setRealizedProfit(Money.money(order.getPortfolio().getRealizedProfit().add(realized)));
        holdingRepository.save(holding);
    }

    private LedgerTransaction toLedgerRecord(TradeOrder order) {
        LedgerTransaction transaction = new LedgerTransaction();
        transaction.setOrder(order);
        transaction.setPortfolio(order.getPortfolio());
        transaction.setUser(order.getUser());
        transaction.setStock(order.getStock());
        transaction.setAction(order.getSide() == OrderSide.BUY ? TransactionAction.BUY : TransactionAction.SELL);
        transaction.setPrice(order.getExecutionPrice());
        transaction.setQuantity(order.getQuantity());
        transaction.setFees(order.getFees());
        return transaction;
    }

    public BigDecimal calculateFee(BigDecimal grossValue) {
        BigDecimal computed = grossValue.multiply(tradingProperties.feeRate()).setScale(4, RoundingMode.HALF_UP);
        if (computed.compareTo(tradingProperties.minFee()) < 0) {
            return Money.money(tradingProperties.minFee());
        }
        if (computed.compareTo(tradingProperties.maxFee()) > 0) {
            return Money.money(tradingProperties.maxFee());
        }
        return Money.money(computed);
    }
}
