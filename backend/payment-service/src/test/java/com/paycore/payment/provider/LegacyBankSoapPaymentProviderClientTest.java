package com.paycore.payment.provider;

import com.paycore.payment.domain.PaymentProviderType;
import com.paycore.payment.provider.soap.dto.AuthorizePaymentRequest;
import com.paycore.payment.provider.soap.dto.AuthorizePaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ws.client.core.WebServiceTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LegacyBankSoapPaymentProviderClientTest {

    private WebServiceTemplate webServiceTemplate;
    private LegacyBankSoapPaymentProviderClient client;

    @BeforeEach
    void setUp() {
        webServiceTemplate = mock(WebServiceTemplate.class);
        client = new LegacyBankSoapPaymentProviderClient(webServiceTemplate);

        ReflectionTestUtils.setField(
                client,
                "legacyBankSoapBaseUrl",
                "http://legacy-bank-soap-service:8084/ws"
        );
    }

    @Test
    void getProviderType_shouldReturnLegacyBankSoap() {
        assertThat(client.getProviderType()).isEqualTo(PaymentProviderType.LEGACY_BANK_SOAP);
    }

    @Test
    void authorize_shouldMapProviderRequestToSoapRequestAndReturnApprovedResponse() {
        UUID merchantId = UUID.randomUUID();

        ProviderPaymentRequest request = new ProviderPaymentRequest(
                merchantId,
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-SOAP-1001",
                "card_token_1234567890123456"
        );

        AuthorizePaymentResponse soapResponse = new AuthorizePaymentResponse();
        soapResponse.setApproved(true);
        soapResponse.setBankReferenceId("LEGACY-BANK-REF-1001");
        soapResponse.setResponseCode("00");
        soapResponse.setResponseMessage("APPROVED");

        when(webServiceTemplate.marshalSendAndReceive(
                eq("http://legacy-bank-soap-service:8084/ws"),
                any(AuthorizePaymentRequest.class)
        )).thenReturn(soapResponse);

        ProviderPaymentResponse response = client.authorize(request);

        assertThat(response).isNotNull();
        assertThat(response.approved()).isTrue();
        assertThat(response.providerReferenceId()).isEqualTo("LEGACY-BANK-REF-1001");
        assertThat(response.responseCode()).isEqualTo("00");
        assertThat(response.responseMessage()).isEqualTo("APPROVED");

        ArgumentCaptor<AuthorizePaymentRequest> soapRequestCaptor =
                ArgumentCaptor.forClass(AuthorizePaymentRequest.class);

        verify(webServiceTemplate).marshalSendAndReceive(
                eq("http://legacy-bank-soap-service:8084/ws"),
                soapRequestCaptor.capture()
        );

        AuthorizePaymentRequest capturedSoapRequest = soapRequestCaptor.getValue();

        assertThat(capturedSoapRequest.getMerchantId()).isEqualTo(merchantId.toString());
        assertThat(capturedSoapRequest.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(capturedSoapRequest.getCurrency()).isEqualTo("TRY");
        assertThat(capturedSoapRequest.getOrderId()).isEqualTo("ORDER-SOAP-1001");
        assertThat(capturedSoapRequest.getCardToken()).isEqualTo("card_token_1234567890123456");

        verifyNoMoreInteractions(webServiceTemplate);
    }

    @Test
    void authorize_shouldReturnRejectedResponse_whenSoapProviderRejectsPayment() {
        UUID merchantId = UUID.randomUUID();

        ProviderPaymentRequest request = new ProviderPaymentRequest(
                merchantId,
                new BigDecimal("150000.00"),
                "TRY",
                "ORDER-SOAP-REJECTED-1001",
                "card_token_1234567890123456"
        );

        AuthorizePaymentResponse soapResponse = new AuthorizePaymentResponse();
        soapResponse.setApproved(false);
        soapResponse.setBankReferenceId(null);
        soapResponse.setResponseCode("LIMIT_EXCEEDED");
        soapResponse.setResponseMessage("Legacy bank authorization limit exceeded");

        when(webServiceTemplate.marshalSendAndReceive(
                eq("http://legacy-bank-soap-service:8084/ws"),
                any(AuthorizePaymentRequest.class)
        )).thenReturn(soapResponse);

        ProviderPaymentResponse response = client.authorize(request);

        assertThat(response).isNotNull();
        assertThat(response.approved()).isFalse();
        assertThat(response.providerReferenceId()).isNull();
        assertThat(response.responseCode()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(response.responseMessage())
                .isEqualTo("Legacy bank authorization limit exceeded");

        ArgumentCaptor<AuthorizePaymentRequest> soapRequestCaptor =
                ArgumentCaptor.forClass(AuthorizePaymentRequest.class);

        verify(webServiceTemplate).marshalSendAndReceive(
                eq("http://legacy-bank-soap-service:8084/ws"),
                soapRequestCaptor.capture()
        );

        AuthorizePaymentRequest capturedSoapRequest = soapRequestCaptor.getValue();

        assertThat(capturedSoapRequest.getMerchantId()).isEqualTo(merchantId.toString());
        assertThat(capturedSoapRequest.getAmount()).isEqualByComparingTo("150000.00");
        assertThat(capturedSoapRequest.getCurrency()).isEqualTo("TRY");
        assertThat(capturedSoapRequest.getOrderId()).isEqualTo("ORDER-SOAP-REJECTED-1001");
        assertThat(capturedSoapRequest.getCardToken()).isEqualTo("card_token_1234567890123456");

        verifyNoMoreInteractions(webServiceTemplate);
    }
}