package com.paycore.payment.service;

import com.paycore.payment.domain.*;
import com.paycore.payment.dto.PaymentNotificationMessage;
import com.paycore.payment.event.PaymentEvent;
import com.paycore.payment.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private JsonMapper jsonMapper;

    private PaymentOutboxService paymentOutboxService;

    @BeforeEach
    void setUp() {
        paymentOutboxService = new PaymentOutboxService(outboxEventRepository, jsonMapper);

        ReflectionTestUtils.setField(paymentOutboxService, "paymentEventsTopic", "payment-events");

        ReflectionTestUtils.setField(paymentOutboxService, "notificationExchange", "notification.exchange");

        ReflectionTestUtils.setField(paymentOutboxService, "paymentNotificationRoutingKey", "payment.notification.created");
    }

    @Test
    void shouldCreateKafkaOutboxEvent() throws Exception {
        Payment payment = createPayment(PaymentStatus.AUTHORIZED);

        AtomicReference<PaymentEvent> serializedEvent = new AtomicReference<>();

        when(jsonMapper.writeValueAsString(any(PaymentEvent.class)))
                .thenAnswer(invocation -> {
                    PaymentEvent event = invocation.getArgument(0);
                    serializedEvent.set(event);
                    return "{\"eventId\":\"" + event.eventId() + "\"}";
                });

        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OutboxEvent result = paymentOutboxService.enqueuePaymentEvent(payment);

        PaymentEvent event = serializedEvent.get();

        assertThat(event).isNotNull();
        assertThat(result.getId()).isEqualTo(event.eventId());
        assertThat(result.getAggregateType()).isEqualTo(OutboxAggregateType.PAYMENT);
        assertThat(result.getAggregateId()).isEqualTo(payment.getId());
        assertThat(result.getEventType()).isEqualTo("PAYMENT_AUTHORIZED");
        assertThat(result.getDestinationType()).isEqualTo(OutboxDestinationType.KAFKA);
        assertThat(result.getDestination()).isEqualTo("payment-events");
        assertThat(result.getMessageKey()).isEqualTo(payment.getId().toString());
        assertThat(result.getRoutingKey()).isNull();
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(result.getPublishedAt()).isNull();

        assertThat(event.paymentId()).isEqualTo(payment.getId());
        assertThat(event.paymentStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(event.providerType()).isEqualTo(PaymentProviderType.MOCK_BANK);

        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void shouldCreateRabbitMqOutboxEvent() throws Exception {
        Payment payment = createPayment(PaymentStatus.FAILED);

        AtomicReference<PaymentNotificationMessage> serializedMessage = new AtomicReference<>();

        when(jsonMapper.writeValueAsString(any(PaymentNotificationMessage.class)))
                .thenAnswer(invocation -> {
                    PaymentNotificationMessage message = invocation.getArgument(0);
                    serializedMessage.set(message);
                    return "{\"notificationId\":\"" + message.notificationId() + "\"}";
                });

        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OutboxEvent result = paymentOutboxService.enqueuePaymentNotification(payment, "Test Merchant");

        PaymentNotificationMessage message = serializedMessage.get();

        assertThat(message).isNotNull();
        assertThat(result.getId()).isEqualTo(message.notificationId());
        assertThat(result.getAggregateType()).isEqualTo(OutboxAggregateType.PAYMENT);
        assertThat(result.getAggregateId()).isEqualTo(payment.getId());
        assertThat(result.getEventType()).isEqualTo("PAYMENT_NOTIFICATION");
        assertThat(result.getDestinationType()).isEqualTo(OutboxDestinationType.RABBITMQ);
        assertThat(result.getDestination()).isEqualTo("notification.exchange");
        assertThat(result.getMessageKey()).isNull();
        assertThat(result.getRoutingKey()).isEqualTo("payment.notification.created");
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(result.getPublishedAt()).isNull();

        assertThat(message.paymentId()).isEqualTo(payment.getId());
        assertThat(message.paymentStatus()).isEqualTo("FAILED");
        assertThat(message.merchantName()).isEqualTo("Test Merchant");

        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void shouldRejectPaymentWithoutId() {
        Payment payment = createPayment(PaymentStatus.INITIATED);
        payment.setId(null);

        assertThatThrownBy(() -> paymentOutboxService.enqueuePaymentEvent(payment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Payment must be persisted before creating an outbox event");

        verifyNoInteractions(outboxEventRepository);
    }

    private Payment createPayment(PaymentStatus status) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .amount(new BigDecimal("1000.00"))
                .currency("TRY")
                .status(status)
                .providerType(PaymentProviderType.MOCK_BANK)
                .orderId("ORDER-OUTBOX-001")
                .providerReferenceId("MOCK-REF-001")
                .providerResponseCode("00")
                .providerResponseMessage("APPROVED")
                .cardLastFourDigits("3456")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
