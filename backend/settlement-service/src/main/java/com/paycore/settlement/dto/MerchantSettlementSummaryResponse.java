package com.paycore.settlement.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MerchantSettlementSummaryResponse(
        UUID merchantId,
        String currency,
        long settlementCount,
        BigDecimal totalGrossAmount,
        BigDecimal totalCommissionAmount,
        BigDecimal totalNetAmount
) {
}