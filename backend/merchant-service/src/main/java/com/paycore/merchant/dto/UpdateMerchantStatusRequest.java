package com.paycore.merchant.dto;

import com.paycore.merchant.domain.MerchantStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateMerchantStatusRequest(

        @NotNull(message = "Merchant status cannot be null")
        MerchantStatus status
) {
}