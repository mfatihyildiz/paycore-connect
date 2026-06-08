package com.paycore.merchant.dto;

import com.paycore.merchant.domain.MerchantStatus;

import java.util.UUID;

public record ApiKeyValidationResponse(
        boolean valid,
        UUID merchantId,
        String merchantName,
        MerchantStatus status
) {
}