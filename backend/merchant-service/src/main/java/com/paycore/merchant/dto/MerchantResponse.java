package com.paycore.merchant.dto;

import com.paycore.merchant.domain.MerchantStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record MerchantResponse(
        UUID id,
        String name,
        String email,
        String apiKey,
        MerchantStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}