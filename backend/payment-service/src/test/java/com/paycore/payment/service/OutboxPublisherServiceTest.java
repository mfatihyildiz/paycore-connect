package com.paycore.payment.service;

import com.paycore.payment.domain.OutboxAggregateType;
import com.paycore.payment.domain.OutboxDestinationType;
import com.paycore.payment.domain.OutboxEvent;
import com.paycore.payment.domain.OutboxStatus;
import com.paycore.payment.event.PaymentEventProducer;
import com.paycore.payment.notification.PaymentNotificationProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceTest {

    @Mock
    private OutboxClaimService outboxClaimService;

    @Mock
    private PaymentEventProducer paymentEventProducer;

    @Mock
    private PaymentNotificationProducer paymentNotificationProducer;

    private OutboxPublisherService outboxPublisherService;

    @BeforeEach
    void setUp() {
        outboxPublisherService = new OutboxPublisherService(outboxClaimService, paymentEventProducer, paymentNotificationProducer);

        ReflectionTestUtils.setField(outboxPublisherService, "batchSize", 10);
        ReflectionTestUtils.setField(outboxPublisherService, "leaseTimeoutSeconds", 120L);
        ReflectionTestUtils.setField(outboxPublisherService, "publishTimeoutSeconds", 5L);
        ReflectionTestUtils.setField(outboxPublisherService, "retryBaseDelaySeconds", 5L);
        ReflectionTestUtils.setField(outboxPublisherService, "retryMaxDelaySeconds", 60L);
    }

    @Test
    void publishPendingBatch_shouldPublishKafkaEventAndMarkPublished() {
        OutboxEvent event = createKafkaEvent();

        when(outboxClaimService.claimBatch(anyString(), eq(10))).thenReturn(List.of(event));
        when(paymentEventProducer.publish(event.getDestination(), event.getMessageKey(), event.getPayload()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        when(outboxClaimService.markPublished(eq(event.getId()), anyString())).thenReturn(true);

        int published = outboxPublisherService.publishPendingBatch();

        assertThat(published).isEqualTo(1);

        verify(outboxClaimService).releaseStaleClaims(Duration.ofSeconds(120));
        verify(outboxClaimService).markPublished(eq(event.getId()), anyString());
        verify(outboxClaimService, never()).registerFailure(eq(event.getId()), anyString(), any(LocalDateTime.class), anyString());
        verify(outboxClaimService, never()).releaseClaim(eq(event.getId()), anyString());
    }

    @Test
    void publishPendingBatch_shouldPublishRabbitMqEventAndMarkPublished() {
        OutboxEvent event = createRabbitEvent();

        when(outboxClaimService.claimBatch(anyString(), eq(10))).thenReturn(List.of(event));
        when(paymentNotificationProducer.publish(event.getDestination(), event.getRoutingKey(), event.getPayload(), event.getId()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(outboxClaimService.markPublished(eq(event.getId()), anyString())).thenReturn(true);

        int published = outboxPublisherService.publishPendingBatch();

        assertThat(published).isEqualTo(1);

        verify(outboxClaimService).markPublished(eq(event.getId()), anyString());
        verify(outboxClaimService, never()).registerFailure(eq(event.getId()), anyString(), any(LocalDateTime.class), anyString());
        verify(outboxClaimService, never()).releaseClaim(eq(event.getId()), anyString());
    }

    @Test
    void publishPendingBatch_shouldRegisterFailure_whenKafkaPublishFails() {
        OutboxEvent event = createKafkaEvent();

        when(outboxClaimService.claimBatch(anyString(), eq(10))).thenReturn(List.of(event));
        when(paymentEventProducer.publish(event.getDestination(), event.getMessageKey(), event.getPayload()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka unavailable")));
        when(outboxClaimService.registerFailure(eq(event.getId()), anyString(), any(LocalDateTime.class), anyString())).thenReturn(true);

        int published = outboxPublisherService.publishPendingBatch();

        assertThat(published).isZero();

        verify(outboxClaimService).registerFailure(eq(event.getId()), anyString(), any(LocalDateTime.class), contains("Kafka unavailable"));
        verify(outboxClaimService, never()).markPublished(eq(event.getId()), anyString());
        verify(outboxClaimService, never()).releaseClaim(eq(event.getId()), anyString());
    }

    @Test
    void publishPendingBatch_shouldRegisterFailure_whenRabbitMqPublishFails() {
        OutboxEvent event = createRabbitEvent();

        when(outboxClaimService.claimBatch(anyString(), eq(10))).thenReturn(List.of(event));
        when(paymentNotificationProducer.publish(event.getDestination(), event.getRoutingKey(), event.getPayload(), event.getId()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("RabbitMQ unavailable")));
        when(outboxClaimService.registerFailure(eq(event.getId()), anyString(), any(LocalDateTime.class), anyString())).thenReturn(true);

        int published = outboxPublisherService.publishPendingBatch();

        assertThat(published).isZero();

        verify(outboxClaimService).registerFailure(eq(event.getId()), anyString(), any(LocalDateTime.class), contains("RabbitMQ unavailable"));
        verify(outboxClaimService, never()).markPublished(eq(event.getId()), anyString());
        verify(outboxClaimService, never()).releaseClaim(eq(event.getId()), anyString());
    }

    @Test
    void publishPendingBatch_shouldDoNothing_whenNoEventsArePending() {
        when(outboxClaimService.claimBatch(anyString(), eq(10))).thenReturn(List.of());

        int published = outboxPublisherService.publishPendingBatch();

        assertThat(published).isZero();

        verify(outboxClaimService).releaseStaleClaims(Duration.ofSeconds(120));
        verify(outboxClaimService).claimBatch(anyString(), eq(10));
        verifyNoInteractions(paymentEventProducer, paymentNotificationProducer);
    }

    private OutboxEvent createKafkaEvent() {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType(OutboxAggregateType.PAYMENT)
                .aggregateId(UUID.randomUUID())
                .eventType("PAYMENT_AUTHORIZED")
                .destinationType(OutboxDestinationType.KAFKA)
                .destination("payment-events")
                .messageKey(UUID.randomUUID().toString())
                .payload("{\"eventId\":\"test\"}")
                .status(OutboxStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .attemptCount(1)
                .lastAttemptAt(LocalDateTime.now())
                .build();
    }

    private OutboxEvent createRabbitEvent() {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType(OutboxAggregateType.PAYMENT)
                .aggregateId(UUID.randomUUID())
                .eventType("PAYMENT_NOTIFICATION")
                .destinationType(OutboxDestinationType.RABBITMQ)
                .destination("notification.exchange")
                .routingKey("payment.notification.created")
                .payload("{\"notificationId\":\"test\"}")
                .status(OutboxStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .attemptCount(1)
                .lastAttemptAt(LocalDateTime.now())
                .build();
    }
}