package com.paycore.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FraudCheckRequest(
        UUID paymentId,
        UUID merchantId,
        BigDecimal amount,
        String currency,
        String orderId,
        String cardToken,
        String ipAddress
) {
}