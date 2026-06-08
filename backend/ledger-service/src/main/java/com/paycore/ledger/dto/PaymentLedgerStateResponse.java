package com.paycore.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentLedgerStateResponse(
        UUID paymentId,
        UUID merchantId,
        BigDecimal amount,
        String currency,
        String orderId,
        String currentStatus,
        String providerType,
        String providerReferenceId,
        String providerResponseCode,
        String providerResponseMessage,
        int eventCount,
        String firstEventType,
        String lastEventType,
        LocalDateTime firstOccurredAt,
        LocalDateTime lastOccurredAt
) {
}