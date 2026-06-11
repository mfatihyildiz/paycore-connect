package com.paycore.legacybank.endpoint;

import com.paycore.legacybank.dto.AuthorizePaymentRequest;
import com.paycore.legacybank.dto.AuthorizePaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyBankEndpointTest {

    private LegacyBankEndpoint legacyBankEndpoint;

    @BeforeEach
    void setUp() {
        legacyBankEndpoint = new LegacyBankEndpoint();
    }

    @Test
    void authorizePayment_shouldApprovePayment_whenAmountIsWithinLimitAndCardTokenIsValid() {
        AuthorizePaymentRequest request = createRequest(
                new BigDecimal("1000.00"),
                "card_token_1234567890123456"
        );

        AuthorizePaymentResponse response = legacyBankEndpoint.authorizePayment(request);

        assertThat(response).isNotNull();
        assertThat(response.isApproved()).isTrue();
        assertThat(response.getBankReferenceId()).isNotNull();
        assertThat(response.getBankReferenceId()).startsWith("LEGACY-BANK-");
        assertThat(response.getResponseCode()).isEqualTo("00");
        assertThat(response.getResponseMessage()).isEqualTo("APPROVED");
    }

    @Test
    void authorizePayment_shouldApprovePayment_whenAmountIsExactlyAtLimit() {
        AuthorizePaymentRequest request = createRequest(
                new BigDecimal("100000.00"),
                "card_token_1234567890123456"
        );

        AuthorizePaymentResponse response = legacyBankEndpoint.authorizePayment(request);

        assertThat(response).isNotNull();
        assertThat(response.isApproved()).isTrue();
        assertThat(response.getBankReferenceId()).isNotNull();
        assertThat(response.getBankReferenceId()).startsWith("LEGACY-BANK-");
        assertThat(response.getResponseCode()).isEqualTo("00");
        assertThat(response.getResponseMessage()).isEqualTo("APPROVED");
    }

    @Test
    void authorizePayment_shouldRejectPayment_whenAmountExceedsLimit() {
        AuthorizePaymentRequest request = createRequest(
                new BigDecimal("100000.01"),
                "card_token_1234567890123456"
        );

        AuthorizePaymentResponse response = legacyBankEndpoint.authorizePayment(request);

        assertThat(response).isNotNull();
        assertThat(response.isApproved()).isFalse();
        assertThat(response.getBankReferenceId()).isNull();
        assertThat(response.getResponseCode()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(response.getResponseMessage()).isEqualTo("Legacy bank authorization limit exceeded");
    }

    @Test
    void authorizePayment_shouldRejectPayment_whenCardTokenIsNull() {
        AuthorizePaymentRequest request = createRequest(
                new BigDecimal("1000.00"),
                null
        );

        AuthorizePaymentResponse response = legacyBankEndpoint.authorizePayment(request);

        assertThat(response).isNotNull();
        assertThat(response.isApproved()).isFalse();
        assertThat(response.getBankReferenceId()).isNull();
        assertThat(response.getResponseCode()).isEqualTo("INVALID_CARD_TOKEN");
        assertThat(response.getResponseMessage()).isEqualTo("Card token is invalid");
    }

    @Test
    void authorizePayment_shouldRejectPayment_whenCardTokenIsBlank() {
        AuthorizePaymentRequest request = createRequest(
                new BigDecimal("1000.00"),
                " "
        );

        AuthorizePaymentResponse response = legacyBankEndpoint.authorizePayment(request);

        assertThat(response).isNotNull();
        assertThat(response.isApproved()).isFalse();
        assertThat(response.getBankReferenceId()).isNull();
        assertThat(response.getResponseCode()).isEqualTo("INVALID_CARD_TOKEN");
        assertThat(response.getResponseMessage()).isEqualTo("Card token is invalid");
    }

    @Test
    void authorizePayment_shouldPrioritizeAmountLimitCheck_whenAmountIsHighAndCardTokenIsInvalid() {
        AuthorizePaymentRequest request = createRequest(
                new BigDecimal("150000.00"),
                " "
        );

        AuthorizePaymentResponse response = legacyBankEndpoint.authorizePayment(request);

        assertThat(response).isNotNull();
        assertThat(response.isApproved()).isFalse();
        assertThat(response.getBankReferenceId()).isNull();
        assertThat(response.getResponseCode()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(response.getResponseMessage()).isEqualTo("Legacy bank authorization limit exceeded");
    }

    private AuthorizePaymentRequest createRequest(BigDecimal amount, String cardToken) {
        AuthorizePaymentRequest request = new AuthorizePaymentRequest();
        request.setMerchantId(UUID.randomUUID().toString());
        request.setAmount(amount);
        request.setCurrency("TRY");
        request.setOrderId("ORDER-LEGACY-BANK-1001");
        request.setCardToken(cardToken);
        return request;
    }
}