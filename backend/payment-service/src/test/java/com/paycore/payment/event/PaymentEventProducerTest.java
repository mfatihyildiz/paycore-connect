package com.paycore.payment.event;

import com.paycore.payment.domain.Payment;
import com.paycore.payment.domain.PaymentProviderType;
import com.paycore.payment.domain.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PaymentEventProducerTest {

    private KafkaTemplate<String, PaymentEvent> kafkaTemplate;
    private PaymentEventProducer paymentEventProducer;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        paymentEventProducer = new PaymentEventProducer(kafkaTemplate);

        ReflectionTestUtils.setField(
                paymentEventProducer,
                "paymentEventsTopic",
                "payment-events-topic"
        );
    }

    @Test
    void publishPaymentEvent_shouldSendPaymentEventToKafka() {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        Payment payment = Payment.builder()
                .id(paymentId)
                .merchantId(merchantId)
                .amount(new BigDecimal("1000.00"))
                .currency("TRY")
                .status(PaymentStatus.AUTHORIZED)
                .providerType(PaymentProviderType.MOCK_BANK)
                .orderId("ORDER-PAYMENT-EVENT-PRODUCER-1001")
                .providerReferenceId("MOCK-BANK-REF-1001")
                .providerResponseCode("00")
                .providerResponseMessage("APPROVED")
                .cardLastFourDigits("3456")
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .updatedAt(LocalDateTime.now())
                .build();

        LocalDateTime beforePublish = LocalDateTime.now();

        paymentEventProducer.publishPaymentEvent(payment);

        LocalDateTime afterPublish = LocalDateTime.now();

        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);

        verify(kafkaTemplate).send(
                eq("payment-events-topic"),
                eq(paymentId.toString()),
                eventCaptor.capture()
        );

        PaymentEvent capturedEvent = eventCaptor.getValue();

        assertThat(capturedEvent.eventId()).isNotNull();
        assertThat(capturedEvent.eventType()).isEqualTo("PAYMENT_AUTHORIZED");
        assertThat(capturedEvent.paymentId()).isEqualTo(paymentId);
        assertThat(capturedEvent.merchantId()).isEqualTo(merchantId);
        assertThat(capturedEvent.amount()).isEqualByComparingTo("1000.00");
        assertThat(capturedEvent.currency()).isEqualTo("TRY");
        assertThat(capturedEvent.orderId()).isEqualTo("ORDER-PAYMENT-EVENT-PRODUCER-1001");
        assertThat(capturedEvent.paymentStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(capturedEvent.providerType()).isEqualTo(PaymentProviderType.MOCK_BANK);
        assertThat(capturedEvent.providerReferenceId()).isEqualTo("MOCK-BANK-REF-1001");
        assertThat(capturedEvent.providerResponseCode()).isEqualTo("00");
        assertThat(capturedEvent.providerResponseMessage()).isEqualTo("APPROVED");
        assertThat(capturedEvent.occurredAt()).isNotNull();
        assertThat(capturedEvent.occurredAt()).isAfterOrEqualTo(beforePublish);
        assertThat(capturedEvent.occurredAt()).isBeforeOrEqualTo(afterPublish);

        verifyNoMoreInteractions(kafkaTemplate);
    }

    @Test
    void publishPaymentEvent_shouldCreateFailedEventType_whenPaymentStatusIsFailed() {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        Payment payment = Payment.builder()
                .id(paymentId)
                .merchantId(merchantId)
                .amount(new BigDecimal("2000.00"))
                .currency("TRY")
                .status(PaymentStatus.FAILED)
                .providerType(PaymentProviderType.MOCK_BANK)
                .orderId("ORDER-PAYMENT-EVENT-PRODUCER-FAILED-1001")
                .providerReferenceId(null)
                .providerResponseCode("LIMIT_EXCEEDED")
                .providerResponseMessage("Payment amount exceeds mock bank authorization limit")
                .cardLastFourDigits("3456")
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .updatedAt(LocalDateTime.now())
                .build();

        paymentEventProducer.publishPaymentEvent(payment);

        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);

        verify(kafkaTemplate).send(
                eq("payment-events-topic"),
                eq(paymentId.toString()),
                eventCaptor.capture()
        );

        PaymentEvent capturedEvent = eventCaptor.getValue();

        assertThat(capturedEvent.eventType()).isEqualTo("PAYMENT_FAILED");
        assertThat(capturedEvent.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(capturedEvent.paymentId()).isEqualTo(paymentId);
        assertThat(capturedEvent.merchantId()).isEqualTo(merchantId);
        assertThat(capturedEvent.providerReferenceId()).isNull();
        assertThat(capturedEvent.providerResponseCode()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(capturedEvent.providerResponseMessage())
                .isEqualTo("Payment amount exceeds mock bank authorization limit");

        verifyNoMoreInteractions(kafkaTemplate);
    }
}