package com.ledgerone.orderqueue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ledgerone.exception.BadRequestException;
import com.ledgerone.exception.ForbiddenOperationException;
import com.ledgerone.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
@Profile("order-worker")
class OrderQueueConfig {
    @Bean
    DefaultErrorHandler orderQueueErrorHandler(KafkaTemplate<String, String> kafka) {
        var deadLetter = new DeadLetterPublishingRecoverer(kafka,
                (record, failure) -> new TopicPartition(record.topic() + "-dlt", record.partition()));
        deadLetter.setFailIfSendResultIsError(true);
        var handler = new DefaultErrorHandler(deadLetter, new FixedBackOff(1000, 2));
        handler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class,
                ConstraintViolationException.class, ResourceNotFoundException.class,
                ForbiddenOperationException.class, BadRequestException.class);
        return handler;
    }
}
