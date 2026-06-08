package com.paycore.fraud.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record FraudCheckRequest(

        @NotNull(message = "Payment id cannot be null")
        UUID paymentId,

        @NotNull(message = "Merchant id cannot be null")
        UUID merchantId,

        @NotNull(message = "Amount cannot be null")
        @DecimalMin(value = "1.00", message = "Amount must be at least 1.00")
        BigDecimal amount,

        @NotBlank(message = "Currency cannot be blank")
        String currency,

        @NotBlank(message = "Order id cannot be blank")
        String orderId,

        @NotBlank(message = "Card token cannot be blank")
        String cardToken,

        String ipAddress
) {
}