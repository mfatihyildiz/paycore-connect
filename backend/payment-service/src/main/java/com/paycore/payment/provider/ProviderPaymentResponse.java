package com.paycore.payment.provider;

public record ProviderPaymentResponse(
        boolean approved,
        String providerReferenceId,
        String responseCode,
        String responseMessage
) {
}