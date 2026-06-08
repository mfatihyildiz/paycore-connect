package com.paycore.payment.client;

import com.paycore.payment.dto.MerchantValidationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class MerchantClient {

    private final RestClient restClient;

    @Value("${services.merchant-service.base-url}")
    private String merchantServiceBaseUrl;

    public MerchantValidationResponse validateApiKey(String apiKey) {
        return restClient.get()
                .uri(merchantServiceBaseUrl + "/api/merchants/validate-api-key")
                .header("X-API-Key", apiKey)
                .retrieve()
                .body(MerchantValidationResponse.class);
    }
}