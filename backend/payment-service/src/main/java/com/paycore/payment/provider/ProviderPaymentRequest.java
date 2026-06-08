package com.paycore.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;

public record ProviderPaymentRequest(
        UUID merchantId,
        BigDecimal amount,
        String currency,
        String orderId,
        String cardToken
) {
}