package com.paycore.payment.dto;

import com.paycore.payment.domain.PaymentProviderType;
import com.paycore.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID merchantId,
        BigDecimal amount,
        String currency,
        String orderId,
        PaymentStatus status,
        PaymentProviderType providerType,
        String providerReferenceId,
        String providerResponseCode,
        String providerResponseMessage,
        String cardLastFourDigits,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}