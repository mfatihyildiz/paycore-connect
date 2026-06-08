package com.paycore.payment.provider;

import com.paycore.payment.domain.PaymentProviderType;

public interface PaymentProviderClient {

    PaymentProviderType getProviderType();

    ProviderPaymentResponse authorize(ProviderPaymentRequest request);
}