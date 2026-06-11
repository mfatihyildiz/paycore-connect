package com.paycore.payment.client;

import com.paycore.payment.dto.MerchantValidationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class MerchantClientTest {

    private MockRestServiceServer mockServer;
    private MerchantClient merchantClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();

        mockServer = MockRestServiceServer
                .bindTo(restClientBuilder)
                .build();

        RestClient restClient = restClientBuilder.build();

        merchantClient = new MerchantClient(restClient);

        ReflectionTestUtils.setField(
                merchantClient,
                "merchantServiceBaseUrl",
                "http://merchant-service:8081"
        );
    }

    @Test
    void validateApiKey_shouldCallMerchantServiceAndReturnValidationResponse() {
        UUID merchantId = UUID.randomUUID();

        String responseBody = """
                {
                  "valid": true,
                  "merchantId": "%s",
                  "merchantName": "Test Merchant",
                  "status": "ACTIVE"
                }
                """.formatted(merchantId);

        mockServer.expect(once(), requestTo(
                        "http://merchant-service:8081/api/merchants/validate-api-key"
                ))
                .andExpect(method(GET))
                .andExpect(header("X-API-Key", "pk_live_valid_key"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        MerchantValidationResponse response = merchantClient.validateApiKey("pk_live_valid_key");

        assertThat(response).isNotNull();
        assertThat(response.valid()).isTrue();
        assertThat(response.merchantId()).isEqualTo(merchantId);
        assertThat(response.merchantName()).isEqualTo("Test Merchant");
        assertThat(response.status()).isEqualTo("ACTIVE");

        mockServer.verify();
    }

    @Test
    void validateApiKey_shouldReturnInvalidResponse_whenMerchantServiceReturnsInvalidApiKey() {
        String responseBody = """
                {
                  "valid": false,
                  "merchantId": null,
                  "merchantName": null,
                  "status": null
                }
                """;

        mockServer.expect(once(), requestTo(
                        "http://merchant-service:8081/api/merchants/validate-api-key"
                ))
                .andExpect(method(GET))
                .andExpect(header("X-API-Key", "invalid-api-key"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        MerchantValidationResponse response = merchantClient.validateApiKey("invalid-api-key");

        assertThat(response).isNotNull();
        assertThat(response.valid()).isFalse();
        assertThat(response.merchantId()).isNull();
        assertThat(response.merchantName()).isNull();
        assertThat(response.status()).isNull();

        mockServer.verify();
    }

    @Test
    void validateApiKey_shouldPropagateException_whenMerchantServiceReturnsServerError() {
        mockServer.expect(once(), requestTo(
                        "http://merchant-service:8081/api/merchants/validate-api-key"
                ))
                .andExpect(method(GET))
                .andExpect(header("X-API-Key", "pk_live_valid_key"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> merchantClient.validateApiKey("pk_live_valid_key"))
                .isInstanceOf(RestClientResponseException.class);

        mockServer.verify();
    }
}