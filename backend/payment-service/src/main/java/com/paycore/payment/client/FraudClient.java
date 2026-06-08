package com.paycore.payment.client;

import com.paycore.payment.dto.FraudCheckRequest;
import com.paycore.payment.dto.FraudCheckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class FraudClient {

    private final RestClient restClient;

    @Value("${services.fraud-service.base-url}")
    private String fraudServiceBaseUrl;

    public FraudCheckResponse checkPaymentRisk(FraudCheckRequest request) {
        return restClient.post()
                .uri(fraudServiceBaseUrl + "/api/fraud/check")
                .body(request)
                .retrieve()
                .body(FraudCheckResponse.class);
    }
}