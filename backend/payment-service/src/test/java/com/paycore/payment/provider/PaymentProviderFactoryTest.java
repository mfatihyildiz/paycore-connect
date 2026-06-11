package com.paycore.payment.provider;

import com.paycore.payment.domain.PaymentProviderType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentProviderFactoryTest {

    @Test
    void getClient_shouldReturnMatchingClient_whenProviderTypeIsSupported() {
        PaymentProviderClient mockBankClient = new TestPaymentProviderClient(
                PaymentProviderType.MOCK_BANK
        );

        PaymentProviderFactory factory = new PaymentProviderFactory(
                List.of(mockBankClient)
        );

        PaymentProviderClient selectedClient = factory.getClient(PaymentProviderType.MOCK_BANK);

        assertThat(selectedClient).isSameAs(mockBankClient);
        assertThat(selectedClient.getProviderType()).isEqualTo(PaymentProviderType.MOCK_BANK);
    }

    @Test
    void getClient_shouldThrowIllegalArgumentException_whenProviderTypeIsNotSupported() {
        PaymentProviderClient mockBankClient = new TestPaymentProviderClient(
                PaymentProviderType.MOCK_BANK
        );

        PaymentProviderFactory factory = new PaymentProviderFactory(
                List.of(mockBankClient)
        );

        assertThatThrownBy(() -> factory.getClient(PaymentProviderType.REST_BANK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported payment provider type")
                .hasMessageContaining("REST_BANK");
    }

    @Test
    void constructor_shouldRegisterMultipleProviderClients() {
        PaymentProviderClient mockBankClient = new TestPaymentProviderClient(
                PaymentProviderType.MOCK_BANK
        );

        PaymentProviderClient legacyBankSoapClient = new TestPaymentProviderClient(
                PaymentProviderType.LEGACY_BANK_SOAP
        );

        PaymentProviderFactory factory = new PaymentProviderFactory(
                List.of(mockBankClient, legacyBankSoapClient)
        );

        assertThat(factory.getClient(PaymentProviderType.MOCK_BANK))
                .isSameAs(mockBankClient);

        assertThat(factory.getClient(PaymentProviderType.LEGACY_BANK_SOAP))
                .isSameAs(legacyBankSoapClient);
    }

    private static class TestPaymentProviderClient implements PaymentProviderClient {

        private final PaymentProviderType providerType;

        private TestPaymentProviderClient(PaymentProviderType providerType) {
            this.providerType = providerType;
        }

        @Override
        public PaymentProviderType getProviderType() {
            return providerType;
        }

        @Override
        public ProviderPaymentResponse authorize(ProviderPaymentRequest request) {
            return new ProviderPaymentResponse(
                    true,
                    "TEST-PROVIDER-" + UUID.randomUUID(),
                    "00",
                    "APPROVED"
            );
        }
    }
}