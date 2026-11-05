package com.ledgerone.orderqueue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerone.dto.ApiResponse;
import com.ledgerone.dto.TradingDtos;
import com.ledgerone.entity.UserAccount;
import com.ledgerone.exception.ForbiddenOperationException;
import com.ledgerone.security.CurrentUser;
import com.ledgerone.service.PortfolioService;
import com.ledgerone.service.TradingService;
import jakarta.validation.Valid;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/queued")
@Profile("order-api")
@RequiredArgsConstructor
class QueuedOrderController {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final CurrentUser currentUser;
    private final PortfolioService portfolios;
    private final TradingService trading;

    @Value("${ledgerone.orders.topic}")
    private String topic;

    @PostMapping
    ResponseEntity<ApiResponse<Receipt>> enqueue(@Valid @RequestBody TradingDtos.OrderRequest order)
            throws JsonProcessingException {
        UserAccount user = currentUser.entity();
        if (user.isFrozen()) {
            throw new ForbiddenOperationException("Frozen accounts cannot place orders");
        }
        portfolios.getOwnedPortfolio(user, order.portfolioId());
        String message = json.writeValueAsString(new QueuedOrder(user.getId(), order));
        try {
            kafka.send(topic, user.getId().toString(), message).get(7, TimeUnit.SECONDS);
            return ResponseEntity.accepted().body(ApiResponse.ok("Order queued", new Receipt(order.clientOrderId())));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return unavailable();
        } catch (ExecutionException | TimeoutException | KafkaException | org.apache.kafka.common.KafkaException exception) {
            return unavailable();
        }
    }

    @GetMapping
    ApiResponse<TradingDtos.OrderResponse> status(@RequestParam String clientOrderId) {
        return ApiResponse.ok("Order loaded", trading.findOrder(currentUser.entity(), clientOrderId));
    }

    private ResponseEntity<ApiResponse<Receipt>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("Queue acknowledgement unavailable. Retry with the same clientOrderId.", null));
    }

    record Receipt(String clientOrderId) {}
}
