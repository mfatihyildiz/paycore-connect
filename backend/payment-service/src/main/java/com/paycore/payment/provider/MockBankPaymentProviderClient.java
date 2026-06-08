package com.paycore.payment.provider;

import com.paycore.payment.domain.PaymentProviderType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class MockBankPaymentProviderClient implements PaymentProviderClient {

    @Override
    public PaymentProviderType getProviderType() {
        return PaymentProviderType.MOCK_BANK;
    }

    @Override
    public ProviderPaymentResponse authorize(ProviderPaymentRequest request) {
        if (request.amount().compareTo(BigDecimal.valueOf(100_000)) > 0) {
            return new ProviderPaymentResponse(
                    false,
                    null,
                    "LIMIT_EXCEEDED",
                    "Payment amount exceeds mock bank authorization limit"
            );
        }

        return new ProviderPaymentResponse(
                true,
                "MOCK-BANK-" + UUID.randomUUID(),
                "00",
                "APPROVED"
        );
    }
}