package com.paycore.settlement.dto;

import com.paycore.settlement.domain.SettlementStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record SettlementResponse(
        UUID id,
        UUID eventId,
        UUID paymentId,
        UUID merchantId,
        BigDecimal grossAmount,
        BigDecimal commissionRate,
        BigDecimal commissionAmount,
        BigDecimal netAmount,
        String currency,
        SettlementStatus status,
        LocalDate settlementDate,
        String orderId,
        String sourceEventType,
        LocalDateTime sourceEventOccurredAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}