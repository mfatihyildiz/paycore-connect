package com.paycore.legacybank.endpoint;

import com.paycore.legacybank.dto.AuthorizePaymentRequest;
import com.paycore.legacybank.dto.AuthorizePaymentResponse;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import java.math.BigDecimal;
import java.util.UUID;

@Endpoint
public class LegacyBankEndpoint {

    private static final String NAMESPACE_URI = "http://paycore.com/legacy-bank";

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "authorizePaymentRequest")
    @ResponsePayload
    public AuthorizePaymentResponse authorizePayment(@RequestPayload AuthorizePaymentRequest request) {
        AuthorizePaymentResponse response = new AuthorizePaymentResponse();

        if (request.getAmount().compareTo(BigDecimal.valueOf(100_000)) > 0) {
            response.setApproved(false);
            response.setBankReferenceId(null);
            response.setResponseCode("LIMIT_EXCEEDED");
            response.setResponseMessage("Legacy bank authorization limit exceeded");
            return response;
        }

        if (request.getCardToken() == null || request.getCardToken().isBlank()) {
            response.setApproved(false);
            response.setBankReferenceId(null);
            response.setResponseCode("INVALID_CARD_TOKEN");
            response.setResponseMessage("Card token is invalid");
            return response;
        }

        response.setApproved(true);
        response.setBankReferenceId("LEGACY-BANK-" + UUID.randomUUID());
        response.setResponseCode("00");
        response.setResponseMessage("APPROVED");

        return response;
    }
}