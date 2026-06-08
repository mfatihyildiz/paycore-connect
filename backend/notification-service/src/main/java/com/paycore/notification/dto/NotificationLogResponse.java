package com.paycore.notification.dto;

import com.paycore.notification.domain.NotificationStatus;
import com.paycore.notification.domain.NotificationType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationLogResponse(
        UUID id,
        UUID paymentId,
        UUID merchantId,
        String merchantName,
        NotificationType notificationType,
        String paymentStatus,
        BigDecimal amount,
        String currency,
        String orderId,
        String providerReferenceId,
        String providerResponseCode,
        String providerResponseMessage,
        NotificationStatus status,
        String simulatedTarget,
        String message,
        LocalDateTime receivedAt,
        LocalDateTime sentAt
) {
}