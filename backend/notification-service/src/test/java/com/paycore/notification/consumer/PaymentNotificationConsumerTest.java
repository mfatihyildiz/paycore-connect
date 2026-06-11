package com.paycore.notification.consumer;

import com.paycore.notification.dto.PaymentNotificationMessage;
import com.paycore.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class PaymentNotificationConsumerTest {

    private NotificationService notificationService;
    private PaymentNotificationConsumer paymentNotificationConsumer;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        paymentNotificationConsumer = new PaymentNotificationConsumer(notificationService);
    }

    @Test
    void consumePaymentNotification_shouldDelegateMessageToNotificationService() {
        PaymentNotificationMessage message = createPaymentNotificationMessage();

        paymentNotificationConsumer.consumePaymentNotification(message);

        verify(notificationService).processPaymentNotification(message);
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void consumePaymentNotification_shouldDelegateNullMessageToNotificationService() {
        paymentNotificationConsumer.consumePaymentNotification(null);

        verify(notificationService).processPaymentNotification(isNull());
        verifyNoMoreInteractions(notificationService);
    }

    private PaymentNotificationMessage createPaymentNotificationMessage() {
        return new PaymentNotificationMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Test Merchant",
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-NOTIFICATION-CONSUMER-1001",
                "AUTHORIZED",
                "MOCK_BANK",
                "MOCK-BANK-REF-1001",
                "00",
                "APPROVED",
                LocalDateTime.now()
        );
    }
}