package com.paycore.payment.provider;

import com.paycore.payment.domain.PaymentProviderType;
import com.paycore.payment.provider.soap.dto.AuthorizePaymentRequest;
import com.paycore.payment.provider.soap.dto.AuthorizePaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;

@Component
@RequiredArgsConstructor
public class LegacyBankSoapPaymentProviderClient implements PaymentProviderClient {

    private final WebServiceTemplate legacyBankWebServiceTemplate;

    @Value("${services.legacy-bank-soap-service.base-url}")
    private String legacyBankSoapBaseUrl;

    @Override
    public PaymentProviderType getProviderType() {
        return PaymentProviderType.LEGACY_BANK_SOAP;
    }

    @Override
    public ProviderPaymentResponse authorize(ProviderPaymentRequest request) {
        AuthorizePaymentRequest soapRequest = new AuthorizePaymentRequest();
        soapRequest.setMerchantId(request.merchantId().toString());
        soapRequest.setAmount(request.amount());
        soapRequest.setCurrency(request.currency());
        soapRequest.setOrderId(request.orderId());
        soapRequest.setCardToken(request.cardToken());

        AuthorizePaymentResponse soapResponse = (AuthorizePaymentResponse)
                legacyBankWebServiceTemplate.marshalSendAndReceive(
                        legacyBankSoapBaseUrl,
                        soapRequest
                );

        return new ProviderPaymentResponse(
                soapResponse.isApproved(),
                soapResponse.getBankReferenceId(),
                soapResponse.getResponseCode(),
                soapResponse.getResponseMessage()
        );
    }
}