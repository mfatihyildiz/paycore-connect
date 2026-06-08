package com.paycore.payment.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record FraudCheckResponse(
        String id,
        UUID paymentId,
        UUID merchantId,
        int riskScore,
        String riskLevel,
        String decision,
        List<String> triggeredRules,
        String message,
        LocalDateTime checkedAt
) {
}