package com.paycore.fraud.dto;

import com.paycore.fraud.domain.FraudDecision;
import com.paycore.fraud.domain.FraudRiskLevel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record FraudCheckResponse(
        String id,
        UUID paymentId,
        UUID merchantId,
        int riskScore,
        FraudRiskLevel riskLevel,
        FraudDecision decision,
        List<String> triggeredRules,
        String message,
        LocalDateTime checkedAt
) {
}