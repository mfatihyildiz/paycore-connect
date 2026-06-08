package com.paycore.notification.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentNotificationMessage(
        UUID notificationId,
        UUID paymentId,
        UUID merchantId,
        String merchantName,
        BigDecimal amount,
        String currency,
        String orderId,
        String paymentStatus,
        String providerType,
        String providerReferenceId,
        String providerResponseCode,
        String providerResponseMessage,
        LocalDateTime occurredAt
) {
}