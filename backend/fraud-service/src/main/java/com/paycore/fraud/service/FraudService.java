package com.paycore.fraud.service;

import com.paycore.fraud.domain.FraudCheck;
import com.paycore.fraud.domain.FraudDecision;
import com.paycore.fraud.domain.FraudRiskLevel;
import com.paycore.fraud.dto.FraudCheckRequest;
import com.paycore.fraud.dto.FraudCheckResponse;
import com.paycore.fraud.repository.FraudCheckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FraudService {

    private final FraudCheckRepository fraudCheckRepository;

    @Value("${paycore.fraud.high-amount-threshold}")
    private BigDecimal highAmountThreshold;

    @Value("${paycore.fraud.card-attempt-threshold}")
    private long cardAttemptThreshold;

    @Value("${paycore.fraud.ip-attempt-threshold}")
    private long ipAttemptThreshold;

    public FraudCheckResponse checkPaymentRisk(FraudCheckRequest request) {
        LocalDateTime now = LocalDateTime.now();

        List<String> triggeredRules = new ArrayList<>();
        int riskScore = 0;

        if (request.amount().compareTo(highAmountThreshold) > 0) {
            riskScore += 50;
            triggeredRules.add("HIGH_AMOUNT");
        }

        LocalDateTime tenMinutesAgo = now.minusMinutes(10);
        long recentCardAttempts = fraudCheckRepository.countByCardTokenAndCheckedAtAfter(
                request.cardToken(),
                tenMinutesAgo
        );

        if (recentCardAttempts >= cardAttemptThreshold) {
            riskScore += 30;
            triggeredRules.add("CARD_VELOCITY_LIMIT");
        }

        if (request.ipAddress() != null && !request.ipAddress().isBlank()) {
            LocalDateTime fiveMinutesAgo = now.minusMinutes(5);
            long recentIpAttempts = fraudCheckRepository.countByIpAddressAndCheckedAtAfter(
                    request.ipAddress(),
                    fiveMinutesAgo
            );

            if (recentIpAttempts >= ipAttemptThreshold) {
                riskScore += 20;
                triggeredRules.add("IP_VELOCITY_LIMIT");
            }
        }

        FraudRiskLevel riskLevel = calculateRiskLevel(riskScore);
        FraudDecision decision = calculateDecision(riskScore);

        FraudCheck fraudCheck = FraudCheck.builder()
                .paymentId(request.paymentId().toString())
                .merchantId(request.merchantId().toString())
                .amount(request.amount())
                .currency(request.currency())
                .orderId(request.orderId())
                .cardToken(request.cardToken())
                .ipAddress(request.ipAddress())
                .riskScore(riskScore)
                .riskLevel(riskLevel)
                .decision(decision)
                .triggeredRules(triggeredRules)
                .checkedAt(now)
                .build();

        FraudCheck savedCheck = fraudCheckRepository.save(fraudCheck);

        return toResponse(savedCheck);
    }

    public List<FraudCheckResponse> getChecksByPaymentId(UUID paymentId) {
        return fraudCheckRepository.findByPaymentId(paymentId.toString())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<FraudCheckResponse> getChecksByMerchantId(UUID merchantId) {
        return fraudCheckRepository.findByMerchantIdOrderByCheckedAtDesc(merchantId.toString())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private FraudRiskLevel calculateRiskLevel(int riskScore) {
        if (riskScore >= 70) {
            return FraudRiskLevel.HIGH;
        }

        if (riskScore >= 30) {
            return FraudRiskLevel.MEDIUM;
        }

        return FraudRiskLevel.LOW;
    }

    private FraudDecision calculateDecision(int riskScore) {
        if (riskScore >= 70) {
            return FraudDecision.REJECTED;
        }

        if (riskScore >= 30) {
            return FraudDecision.REVIEW;
        }

        return FraudDecision.APPROVED;
    }

    private FraudCheckResponse toResponse(FraudCheck fraudCheck) {
        return new FraudCheckResponse(
                fraudCheck.getId(),
                UUID.fromString(fraudCheck.getPaymentId()),
                UUID.fromString(fraudCheck.getMerchantId()),
                fraudCheck.getRiskScore(),
                fraudCheck.getRiskLevel(),
                fraudCheck.getDecision(),
                fraudCheck.getTriggeredRules(),
                buildMessage(fraudCheck),
                fraudCheck.getCheckedAt()
        );
    }

    private String buildMessage(FraudCheck fraudCheck) {
        return switch (fraudCheck.getDecision()) {
            case APPROVED -> "Payment risk is acceptable";
            case REVIEW -> "Payment requires additional review but can continue";
            case REJECTED -> "Payment rejected due to high fraud risk";
        };
    }
}