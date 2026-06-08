package com.paycore.payment.dto;

import com.paycore.payment.domain.PaymentProviderType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PaymentInitiateRequest(

        @NotNull(message = "Amount cannot be null")
        @DecimalMin(value = "1.00", message = "Amount must be at least 1.00")
        BigDecimal amount,

        @NotBlank(message = "Currency cannot be blank")
        @Size(min = 3, max = 3, message = "Currency must be 3 characters")
        String currency,

        @NotBlank(message = "Order id cannot be blank")
        @Size(max = 100, message = "Order id must be at most 100 characters")
        String orderId,

        @NotBlank(message = "Card token cannot be blank")
        String cardToken,

        @NotNull(message = "Provider type cannot be null")
        PaymentProviderType providerType
) {
}