package com.paycore.payment.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PaymentEventProducerTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private PaymentEventProducer paymentEventProducer;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        paymentEventProducer = new PaymentEventProducer(kafkaTemplate);
    }

    @Test
    void publish_shouldSendExactOutboxPayloadToKafka() {
        String topic = "payment-events";
        String messageKey = "payment-id-1001";
        String payload = """
                {"eventId":"event-1001","eventType":"PAYMENT_AUTHORIZED","paymentId":"payment-id-1001"}
                """.trim();

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);

        when(kafkaTemplate.send(topic, messageKey, payload)).thenReturn(future);

        CompletableFuture<SendResult<String, String>> result = paymentEventProducer.publish(topic, messageKey, payload);

        assertThat(result).isSameAs(future);

        verify(kafkaTemplate).send(topic, messageKey, payload);
        verifyNoMoreInteractions(kafkaTemplate);
    }

    @Test
    void publish_shouldReturnFailedKafkaFutureWithoutChangingPayload() {
        String topic = "payment-events";
        String messageKey = "payment-id-1002";
        String payload = """
                {"eventId":"stable-event-id","eventType":"PAYMENT_FAILED"}
                """.trim();

        RuntimeException failure = new RuntimeException("Kafka unavailable");

        CompletableFuture<SendResult<String, String>> failedFuture = CompletableFuture.failedFuture(failure);

        when(kafkaTemplate.send(topic, messageKey, payload)).thenReturn(failedFuture);

        CompletableFuture<SendResult<String, String>> result = paymentEventProducer.publish(topic, messageKey, payload);

        assertThat(result).isSameAs(failedFuture);
        assertThat(result).isCompletedExceptionally();

        verify(kafkaTemplate).send(topic, messageKey, payload);
        verifyNoMoreInteractions(kafkaTemplate);
    }
}