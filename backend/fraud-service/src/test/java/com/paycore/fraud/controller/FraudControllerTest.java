package com.paycore.fraud.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paycore.fraud.domain.FraudDecision;
import com.paycore.fraud.domain.FraudRiskLevel;
import com.paycore.fraud.dto.FraudCheckRequest;
import com.paycore.fraud.dto.FraudCheckResponse;
import com.paycore.fraud.service.FraudService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FraudControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private FraudService fraudService;

    @BeforeEach
    void setUp() {
        fraudService = mock(FraudService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new FraudController(fraudService))
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void checkPaymentRisk_shouldReturnFraudCheckResponse_whenRequestIsValid() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        FraudCheckRequest request = new FraudCheckRequest(
                paymentId,
                merchantId,
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-FRAUD-CONTROLLER-1001",
                "card_token_1234567890123456",
                "192.168.1.10"
        );

        FraudCheckResponse response = new FraudCheckResponse(
                "fraud-check-1",
                paymentId,
                merchantId,
                0,
                FraudRiskLevel.LOW,
                FraudDecision.APPROVED,
                List.of(),
                "Payment risk is acceptable",
                LocalDateTime.now()
        );

        when(fraudService.checkPaymentRisk(any(FraudCheckRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/fraud/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("fraud-check-1"))
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.riskScore").value(0))
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.message").value("Payment risk is acceptable"));

        ArgumentCaptor<FraudCheckRequest> requestCaptor =
                ArgumentCaptor.forClass(FraudCheckRequest.class);

        verify(fraudService).checkPaymentRisk(requestCaptor.capture());

        FraudCheckRequest capturedRequest = requestCaptor.getValue();

        assertThat(capturedRequest.paymentId()).isEqualTo(paymentId);
        assertThat(capturedRequest.merchantId()).isEqualTo(merchantId);
        assertThat(capturedRequest.amount()).isEqualByComparingTo("1000.00");
        assertThat(capturedRequest.currency()).isEqualTo("TRY");
        assertThat(capturedRequest.orderId()).isEqualTo("ORDER-FRAUD-CONTROLLER-1001");
        assertThat(capturedRequest.cardToken()).isEqualTo("card_token_1234567890123456");
        assertThat(capturedRequest.ipAddress()).isEqualTo("192.168.1.10");
    }

    @Test
    void checkPaymentRisk_shouldReturnBadRequest_whenRequestBodyIsInvalid() throws Exception {
        String invalidRequestBody = """
                {
                  "amount": 1000.00,
                  "currency": "",
                  "orderId": "",
                  "cardToken": ""
                }
                """;

        mockMvc.perform(post("/api/fraud/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestBody))
                .andExpect(status().isBadRequest());

        verify(fraudService, never()).checkPaymentRisk(any(FraudCheckRequest.class));
    }

    @Test
    void getChecksByPaymentId_shouldReturnFraudChecksForPayment() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        FraudCheckResponse response = new FraudCheckResponse(
                "fraud-check-payment-1",
                paymentId,
                merchantId,
                50,
                FraudRiskLevel.MEDIUM,
                FraudDecision.REVIEW,
                List.of("HIGH_AMOUNT"),
                "Payment requires additional review but can continue",
                LocalDateTime.now()
        );

        when(fraudService.getChecksByPaymentId(paymentId))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/fraud/payments/{paymentId}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("fraud-check-payment-1"))
                .andExpect(jsonPath("$[0].paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$[0].merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$[0].riskScore").value(50))
                .andExpect(jsonPath("$[0].riskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$[0].decision").value("REVIEW"))
                .andExpect(jsonPath("$[0].triggeredRules[0]").value("HIGH_AMOUNT"));

        verify(fraudService).getChecksByPaymentId(paymentId);
    }

    @Test
    void getChecksByMerchantId_shouldReturnFraudChecksForMerchant() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        FraudCheckResponse response = new FraudCheckResponse(
                "fraud-check-merchant-1",
                paymentId,
                merchantId,
                100,
                FraudRiskLevel.HIGH,
                FraudDecision.REJECTED,
                List.of("HIGH_AMOUNT", "CARD_VELOCITY_LIMIT", "IP_VELOCITY_LIMIT"),
                "Payment rejected due to high fraud risk",
                LocalDateTime.now()
        );

        when(fraudService.getChecksByMerchantId(merchantId))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/fraud/merchants/{merchantId}", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("fraud-check-merchant-1"))
                .andExpect(jsonPath("$[0].paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$[0].merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$[0].riskScore").value(100))
                .andExpect(jsonPath("$[0].riskLevel").value("HIGH"))
                .andExpect(jsonPath("$[0].decision").value("REJECTED"))
                .andExpect(jsonPath("$[0].triggeredRules[0]").value("HIGH_AMOUNT"))
                .andExpect(jsonPath("$[0].triggeredRules[1]").value("CARD_VELOCITY_LIMIT"))
                .andExpect(jsonPath("$[0].triggeredRules[2]").value("IP_VELOCITY_LIMIT"))
                .andExpect(jsonPath("$[0].message").value("Payment rejected due to high fraud risk"));

        verify(fraudService).getChecksByMerchantId(merchantId);
    }
}