package com.paycore.payment.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class PaymentNotificationProducer {

    private final RabbitTemplate rabbitTemplate;

    public CompletableFuture<Void> publish(String exchange, String routingKey, String payload, UUID outboxEventId) {
        Message message = MessageBuilder
                .withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();

        CorrelationData correlationData = new CorrelationData(outboxEventId.toString());

        try {
            rabbitTemplate.send(exchange, routingKey, message, correlationData);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }

        return correlationData.getFuture()
                .thenCompose(confirm -> {
                    if (!confirm.ack()) {
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("RabbitMQ negatively acknowledged outbox event " + outboxEventId + (confirm.reason() != null ? ": " + confirm.reason() : ""))
                        );
                    }

                    if (correlationData.getReturned() != null) {
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("RabbitMQ returned unroutable outbox event " + outboxEventId)
                        );
                    }

                    return CompletableFuture.completedFuture(null);
                });
    }
}