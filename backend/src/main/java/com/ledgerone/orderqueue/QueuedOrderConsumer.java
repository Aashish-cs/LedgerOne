package com.ledgerone.orderqueue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerone.service.TradingService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Profile("order-worker")
@RequiredArgsConstructor
class QueuedOrderConsumer {
    private final ObjectMapper json;
    private final Validator validator;
    private final TradingService trading;

    @KafkaListener(topics = "${ledgerone.orders.topic}")
    void process(String message) throws JsonProcessingException {
        QueuedOrder queued = json.readValue(message, QueuedOrder.class);
        if (queued == null) {
            throw new IllegalArgumentException("An order message is required");
        }
        var violations = validator.validate(queued);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        trading.placeOrder(queued.userId(), queued.order());
    }
}
