package com.paycore.merchant.dto;

import java.util.UUID;

public record GenerateApiKeyResponse(
        UUID merchantId,
        String apiKey
) {
}