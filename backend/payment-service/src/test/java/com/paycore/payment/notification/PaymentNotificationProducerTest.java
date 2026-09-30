package com.paycore.payment.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentNotificationProducerTest {

    private RabbitTemplate rabbitTemplate;
    private PaymentNotificationProducer producer;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        producer = new PaymentNotificationProducer(rabbitTemplate);
    }

    @Test
    void publish_shouldCompleteSuccessfully_whenRabbitMqAcknowledgesMessage() {
        String exchange = "notification.exchange";
        String routingKey = "payment.notification.created";
        String payload = "{\"notificationId\":\"test\"}";
        UUID eventId = UUID.randomUUID();

        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));

            return null;
        }).when(rabbitTemplate).send(eq(exchange), eq(routingKey), any(Message.class), any(CorrelationData.class));

        CompletableFuture<Void> future = producer.publish(exchange, routingKey, payload, eventId);

        future.join();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

        verify(rabbitTemplate).send(eq(exchange), eq(routingKey), messageCaptor.capture(), any(CorrelationData.class));

        Message message = messageCaptor.getValue();

        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo(payload);
    }

    @Test
    void publish_shouldFail_whenRabbitMqReturnsNack() {
        String exchange = "notification.exchange";
        String routingKey = "payment.notification.created";
        String payload = "{\"notificationId\":\"test\"}";
        UUID eventId = UUID.randomUUID();

        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);

            correlationData.getFuture().complete(new CorrelationData.Confirm(false, "nack"));

            return null;
        }).when(rabbitTemplate).send(eq(exchange), eq(routingKey), any(Message.class), any(CorrelationData.class));

        CompletableFuture<Void> future = producer.publish(exchange, routingKey, payload, eventId);

        assertThatThrownBy(future::join).hasCauseInstanceOf(IllegalStateException.class);
    }
}