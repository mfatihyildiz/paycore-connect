package com.paycore.payment.notification;

import com.paycore.payment.domain.Payment;
import com.paycore.payment.domain.PaymentProviderType;
import com.paycore.payment.domain.PaymentStatus;
import com.paycore.payment.dto.PaymentNotificationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PaymentNotificationProducerTest {

    private RabbitTemplate rabbitTemplate;
    private PaymentNotificationProducer paymentNotificationProducer;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        paymentNotificationProducer = new PaymentNotificationProducer(rabbitTemplate);

        ReflectionTestUtils.setField(
                paymentNotificationProducer,
                "exchange",
                "payment-exchange"
        );

        ReflectionTestUtils.setField(
                paymentNotificationProducer,
                "paymentNotificationRoutingKey",
                "payment-notification-routing-key"
        );
    }

    @Test
    void publishPaymentNotification_shouldSendNotificationMessageToRabbitMq() {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        Payment payment = Payment.builder()
                .id(paymentId)
                .merchantId(merchantId)
                .amount(new BigDecimal("1000.00"))
                .currency("TRY")
                .status(PaymentStatus.AUTHORIZED)
                .providerType(PaymentProviderType.MOCK_BANK)
                .orderId("ORDER-PAYMENT-NOTIFICATION-PRODUCER-1001")
                .providerReferenceId("MOCK-BANK-REF-1001")
                .providerResponseCode("00")
                .providerResponseMessage("APPROVED")
                .cardLastFourDigits("3456")
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .updatedAt(LocalDateTime.now())
                .build();

        LocalDateTime beforePublish = LocalDateTime.now();

        paymentNotificationProducer.publishPaymentNotification(payment, "Test Merchant");

        LocalDateTime afterPublish = LocalDateTime.now();

        ArgumentCaptor<PaymentNotificationMessage> messageCaptor =
                ArgumentCaptor.forClass(PaymentNotificationMessage.class);

        verify(rabbitTemplate).convertAndSend(
                eq("payment-exchange"),
                eq("payment-notification-routing-key"),
                messageCaptor.capture()
        );

        PaymentNotificationMessage capturedMessage = messageCaptor.getValue();

        assertThat(capturedMessage.notificationId()).isNotNull();
        assertThat(capturedMessage.paymentId()).isEqualTo(paymentId);
        assertThat(capturedMessage.merchantId()).isEqualTo(merchantId);
        assertThat(capturedMessage.merchantName()).isEqualTo("Test Merchant");
        assertThat(capturedMessage.amount()).isEqualByComparingTo("1000.00");
        assertThat(capturedMessage.currency()).isEqualTo("TRY");
        assertThat(capturedMessage.orderId()).isEqualTo("ORDER-PAYMENT-NOTIFICATION-PRODUCER-1001");
        assertThat(capturedMessage.paymentStatus()).isEqualTo("AUTHORIZED");
        assertThat(capturedMessage.providerType()).isEqualTo("MOCK_BANK");
        assertThat(capturedMessage.providerReferenceId()).isEqualTo("MOCK-BANK-REF-1001");
        assertThat(capturedMessage.providerResponseCode()).isEqualTo("00");
        assertThat(capturedMessage.providerResponseMessage()).isEqualTo("APPROVED");
        assertThat(capturedMessage.occurredAt()).isNotNull();
        assertThat(capturedMessage.occurredAt()).isAfterOrEqualTo(beforePublish);
        assertThat(capturedMessage.occurredAt()).isBeforeOrEqualTo(afterPublish);

        verifyNoMoreInteractions(rabbitTemplate);
    }

    @Test
    void publishPaymentNotification_shouldSendFailedPaymentNotificationMessage() {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        Payment payment = Payment.builder()
                .id(paymentId)
                .merchantId(merchantId)
                .amount(new BigDecimal("2000.00"))
                .currency("TRY")
                .status(PaymentStatus.FAILED)
                .providerType(PaymentProviderType.MOCK_BANK)
                .orderId("ORDER-PAYMENT-NOTIFICATION-FAILED-1001")
                .providerReferenceId(null)
                .providerResponseCode("LIMIT_EXCEEDED")
                .providerResponseMessage("Payment amount exceeds mock bank authorization limit")
                .cardLastFourDigits("3456")
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .updatedAt(LocalDateTime.now())
                .build();

        paymentNotificationProducer.publishPaymentNotification(payment, "Failed Merchant");

        ArgumentCaptor<PaymentNotificationMessage> messageCaptor =
                ArgumentCaptor.forClass(PaymentNotificationMessage.class);

        verify(rabbitTemplate).convertAndSend(
                eq("payment-exchange"),
                eq("payment-notification-routing-key"),
                messageCaptor.capture()
        );

        PaymentNotificationMessage capturedMessage = messageCaptor.getValue();

        assertThat(capturedMessage.notificationId()).isNotNull();
        assertThat(capturedMessage.paymentId()).isEqualTo(paymentId);
        assertThat(capturedMessage.merchantId()).isEqualTo(merchantId);
        assertThat(capturedMessage.merchantName()).isEqualTo("Failed Merchant");
        assertThat(capturedMessage.amount()).isEqualByComparingTo("2000.00");
        assertThat(capturedMessage.currency()).isEqualTo("TRY");
        assertThat(capturedMessage.orderId()).isEqualTo("ORDER-PAYMENT-NOTIFICATION-FAILED-1001");
        assertThat(capturedMessage.paymentStatus()).isEqualTo("FAILED");
        assertThat(capturedMessage.providerType()).isEqualTo("MOCK_BANK");
        assertThat(capturedMessage.providerReferenceId()).isNull();
        assertThat(capturedMessage.providerResponseCode()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(capturedMessage.providerResponseMessage())
                .isEqualTo("Payment amount exceeds mock bank authorization limit");
        assertThat(capturedMessage.occurredAt()).isNotNull();

        verifyNoMoreInteractions(rabbitTemplate);
    }
}