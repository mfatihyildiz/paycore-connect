package com.paycore.merchant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMerchantRequest(

        @NotBlank(message = "Merchant name cannot be blank")
        @Size(max = 150, message = "Merchant name must be at most 150 characters")
        String name,

        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Email format is invalid")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email
) {
}