package com.ledgerone.orderqueue;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerone.dto.TradingDtos;
import com.ledgerone.entity.*;
import com.ledgerone.security.CurrentUser;
import com.ledgerone.service.*;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

class QueuedOrderTest {
    @SuppressWarnings("unchecked") KafkaTemplate<String,String> kafka = mock(KafkaTemplate.class);
    ObjectMapper json = new ObjectMapper();
    CurrentUser current = mock(CurrentUser.class);
    PortfolioService portfolios = mock(PortfolioService.class);
    TradingService trading = mock(TradingService.class);
    UserAccount user = new UserAccount();
    QueuedOrderController api;
    TradingDtos.OrderRequest order;
    @BeforeEach void setup() {
        user.setId(UUID.randomUUID());
        order = new TradingDtos.OrderRequest(UUID.randomUUID(),"AAPL",OrderSide.BUY,OrderType.MARKET,BigDecimal.ONE,null,"id-1");
        api = new QueuedOrderController(kafka,json,current,portfolios,trading);
        ReflectionTestUtils.setField(api,"topic","orders");
        when(current.entity()).thenReturn(user);
    }
    @Test void acceptedResponseIncludesAcknowledgedBrokerOffset() throws Exception {
        var metadata = new RecordMetadata(new TopicPartition("orders",2),40,0,0,1,1);
        when(kafka.send(anyString(),anyString(),anyString())).thenReturn(CompletableFuture.completedFuture(
                new SendResult<>(new ProducerRecord<>("orders","key","value"),metadata)));
        var result = api.enqueue(order);
        assertThat(result.getStatusCode().value()).isEqualTo(202);
        assertThat(result.getBody().data().offset()).isEqualTo(40);
        assertThat(result.getBody().data().requestId()).isNotNull();
        verify(kafka).send(eq("orders"),eq(user.getId().toString()),anyString());
    }
    @Test void brokerFailureDoesNotReturnAcceptance() throws Exception {
        when(kafka.send(anyString(),anyString(),anyString())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        assertThat(api.enqueue(order).getStatusCode().value()).isEqualTo(503);
    }
    @Test void producerConstructionFailureDoesNotReturnAcceptance() throws Exception {
        when(kafka.send(anyString(),anyString(),anyString())).thenThrow(new org.apache.kafka.common.KafkaException("unavailable"));
        assertThat(api.enqueue(order).getStatusCode().value()).isEqualTo(503);
    }
    @Test void frozenAccountCannotPublish() {
        user.setStatus(AccountStatus.FROZEN);
        assertThatThrownBy(() -> api.enqueue(order)).isInstanceOf(com.ledgerone.exception.ForbiddenOperationException.class);
        verifyNoInteractions(kafka);
    }
    @Test void consumerRejectsInvalidPayloadBeforeTrading() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var consumer=new QueuedOrderConsumer(json,factory.getValidator(),trading);
            assertThatThrownBy(() -> consumer.process("{\"userId\":null,\"order\":null}")).isInstanceOf(jakarta.validation.ConstraintViolationException.class);
            verifyNoInteractions(trading);
        }
    }
    @Test void validMessageUsesAuthenticatedUserIdFromEvent() throws Exception {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var consumer=new QueuedOrderConsumer(json,factory.getValidator(),trading);
            consumer.process(json.writeValueAsString(new QueuedOrder(user.getId(),order)));
            verify(trading).placeOrder(user.getId(),order);
        }
    }
}
