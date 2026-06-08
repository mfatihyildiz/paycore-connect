package com.paycore.payment.event;

import com.paycore.payment.domain.PaymentProviderType;
import com.paycore.payment.domain.PaymentStatus;

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
        PaymentStatus paymentStatus,
        PaymentProviderType providerType,
        String providerReferenceId,
        String providerResponseCode,
        String providerResponseMessage,
        LocalDateTime occurredAt
) {
}