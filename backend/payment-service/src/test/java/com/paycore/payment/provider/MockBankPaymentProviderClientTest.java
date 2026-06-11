package com.paycore.payment.provider;

import com.paycore.payment.domain.PaymentProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MockBankPaymentProviderClientTest {

    private MockBankPaymentProviderClient mockBankPaymentProviderClient;

    @BeforeEach
    void setUp() {
        mockBankPaymentProviderClient = new MockBankPaymentProviderClient();
    }

    @Test
    void getProviderType_shouldReturnMockBank() {
        PaymentProviderType providerType = mockBankPaymentProviderClient.getProviderType();

        assertThat(providerType).isEqualTo(PaymentProviderType.MOCK_BANK);
    }

    @Test
    void authorize_shouldApprovePayment_whenAmountIsWithinLimit() {
        ProviderPaymentRequest request = createProviderPaymentRequest(
                new BigDecimal("1000.00")
        );

        ProviderPaymentResponse response = mockBankPaymentProviderClient.authorize(request);

        assertThat(response).isNotNull();
        assertThat(response.approved()).isTrue();
        assertThat(response.providerReferenceId()).isNotNull();
        assertThat(response.providerReferenceId()).startsWith("MOCK-BANK-");
        assertThat(response.responseCode()).isEqualTo("00");
        assertThat(response.responseMessage()).isEqualTo("APPROVED");
    }

    @Test
    void authorize_shouldApprovePayment_whenAmountIsExactlyAtLimit() {
        ProviderPaymentRequest request = createProviderPaymentRequest(
                new BigDecimal("100000.00")
        );

        ProviderPaymentResponse response = mockBankPaymentProviderClient.authorize(request);

        assertThat(response).isNotNull();
        assertThat(response.approved()).isTrue();
        assertThat(response.providerReferenceId()).isNotNull();
        assertThat(response.providerReferenceId()).startsWith("MOCK-BANK-");
        assertThat(response.responseCode()).isEqualTo("00");
        assertThat(response.responseMessage()).isEqualTo("APPROVED");
    }

    @Test
    void authorize_shouldRejectPayment_whenAmountExceedsLimit() {
        ProviderPaymentRequest request = createProviderPaymentRequest(
                new BigDecimal("100000.01")
        );

        ProviderPaymentResponse response = mockBankPaymentProviderClient.authorize(request);

        assertThat(response).isNotNull();
        assertThat(response.approved()).isFalse();
        assertThat(response.providerReferenceId()).isNull();
        assertThat(response.responseCode()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(response.responseMessage()).isEqualTo("Payment amount exceeds mock bank authorization limit");
    }

    private ProviderPaymentRequest createProviderPaymentRequest(BigDecimal amount) {
        return new ProviderPaymentRequest(
                UUID.randomUUID(),
                amount,
                "TRY",
                "ORDER-MOCK-BANK-1001",
                "card_token_1234567890123456"
        );
    }
}