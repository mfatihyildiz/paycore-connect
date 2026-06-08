package com.paycore.fraud.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "fraud_checks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudCheck {

    @Id
    private String id;

    private String paymentId;
    private String merchantId;

    private BigDecimal amount;
    private String currency;
    private String orderId;
    private String cardToken;
    private String ipAddress;

    private int riskScore;
    private FraudRiskLevel riskLevel;
    private FraudDecision decision;

    private List<String> triggeredRules;

    private LocalDateTime checkedAt;
}