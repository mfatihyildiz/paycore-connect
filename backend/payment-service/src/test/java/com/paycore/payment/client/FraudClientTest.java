package com.paycore.payment.client;

import com.paycore.payment.dto.FraudCheckRequest;
import com.paycore.payment.dto.FraudCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FraudClientTest {

    private MockRestServiceServer mockServer;
    private FraudClient fraudClient;

    @BeforeEach
    void setUp() {

        RestClient.Builder builder = RestClient.builder();

        mockServer = MockRestServiceServer.bindTo(builder).build();

        RestClient restClient = builder.build();

        fraudClient = new FraudClient(restClient);

        ReflectionTestUtils.setField(
                fraudClient,
                "fraudServiceBaseUrl",
                "http://fraud-service:8082"
        );
    }

    @Test
    void checkPaymentRisk_shouldReturnFraudResponse() {

        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        FraudCheckRequest request = new FraudCheckRequest(
                paymentId,
                merchantId,
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-1001",
                "TOKEN-1234",
                "127.0.0.1"
        );

        String response = """
                {
                  "id":"fraud-check-id",
                  "paymentId":"%s",
                  "merchantId":"%s",
                  "riskScore":30,
                  "riskLevel":"MEDIUM",
                  "decision":"REVIEW",
                  "triggeredRules":["HIGH_AMOUNT"],
                  "message":"Payment requires additional review but can continue",
                  "checkedAt":"2025-01-01T10:00:00"
                }
                """.formatted(paymentId, merchantId);

        mockServer.expect(once(),
                        requestTo("http://fraud-service:8082/api/fraud/check"))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        FraudCheckResponse result = fraudClient.checkPaymentRisk(request);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo("fraud-check-id");
        assertThat(result.paymentId()).isEqualTo(paymentId);
        assertThat(result.merchantId()).isEqualTo(merchantId);
        assertThat(result.riskScore()).isEqualTo(30);
        assertThat(result.riskLevel()).isEqualTo("MEDIUM");
        assertThat(result.decision()).isEqualTo("REVIEW");
        assertThat(result.triggeredRules()).isEqualTo(List.of("HIGH_AMOUNT"));
        assertThat(result.message())
                .isEqualTo("Payment requires additional review but can continue");
        assertThat(result.checkedAt())
                .isEqualTo(LocalDateTime.parse("2025-01-01T10:00:00"));

        mockServer.verify();
    }

    @Test
    void checkPaymentRisk_shouldReturnRejectedFraudResponse() {

        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        FraudCheckRequest request = new FraudCheckRequest(
                paymentId,
                merchantId,
                new BigDecimal("500000.00"),
                "TRY",
                "ORDER-2001",
                "TOKEN-9999",
                "10.0.0.5"
        );

        String response = """
                {
                  "id":"fraud-check-id-2",
                  "paymentId":"%s",
                  "merchantId":"%s",
                  "riskScore":80,
                  "riskLevel":"HIGH",
                  "decision":"REJECTED",
                  "triggeredRules":["HIGH_AMOUNT","CARD_VELOCITY_LIMIT"],
                  "message":"Payment rejected due to high fraud risk",
                  "checkedAt":"2025-01-01T11:00:00"
                }
                """.formatted(paymentId, merchantId);

        mockServer.expect(once(),
                        requestTo("http://fraud-service:8082/api/fraud/check"))
                .andExpect(method(POST))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        FraudCheckResponse result = fraudClient.checkPaymentRisk(request);

        assertThat(result.decision()).isEqualTo("REJECTED");
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.riskScore()).isEqualTo(80);
        assertThat(result.triggeredRules())
                .containsExactly("HIGH_AMOUNT", "CARD_VELOCITY_LIMIT");

        mockServer.verify();
    }

    @Test
    void checkPaymentRisk_shouldThrowException_whenFraudServiceReturnsServerError() {

        FraudCheckRequest request = new FraudCheckRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                "TRY",
                "ORDER-3001",
                "TOKEN",
                "127.0.0.1"
        );

        mockServer.expect(once(),
                        requestTo("http://fraud-service:8082/api/fraud/check"))
                .andExpect(method(POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> fraudClient.checkPaymentRisk(request))
                .isInstanceOf(RestClientResponseException.class);

        mockServer.verify();
    }

}