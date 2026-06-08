package com.paycore.payment.dto;

import java.util.UUID;

public record MerchantValidationResponse(
        boolean valid,
        UUID merchantId,
        String merchantName,
        String status
) {
}