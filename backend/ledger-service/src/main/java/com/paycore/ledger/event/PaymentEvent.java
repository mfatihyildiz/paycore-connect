package com.paycore.ledger.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentEvent(
        UUID eventId,
        String eventType,
        UUID paymentId,
        UUID merchantId,
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