package com.paycore.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paycore.payment.domain.PaymentProviderType;
import com.paycore.payment.domain.PaymentStatus;
import com.paycore.payment.dto.PaymentInitiateRequest;
import com.paycore.payment.dto.PaymentResponse;
import com.paycore.payment.exception.DuplicateOrderException;
import com.paycore.payment.exception.GlobalExceptionHandler;
import com.paycore.payment.exception.InvalidMerchantApiKeyException;
import com.paycore.payment.exception.PaymentNotFoundException;
import com.paycore.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PaymentController(paymentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void initiatePayment_shouldReturnCreatedPayment_whenRequestIsValid() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        PaymentInitiateRequest request = new PaymentInitiateRequest(
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-PAYMENT-CONTROLLER-1001",
                "card_token_1234567890123456",
                PaymentProviderType.MOCK_BANK
        );

        PaymentResponse response = createPaymentResponse(
                paymentId,
                merchantId,
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-PAYMENT-CONTROLLER-1001",
                PaymentStatus.AUTHORIZED,
                PaymentProviderType.MOCK_BANK,
                "MOCK-BANK-REF-1001",
                "00",
                "APPROVED",
                "3456"
        );

        when(paymentService.initiatePayment(
                anyString(),
                anyString(),
                any(PaymentInitiateRequest.class)
        )).thenReturn(response);

        mockMvc.perform(post("/api/payments/initiate")
                        .header("X-API-Key", "pk_live_valid_key")
                        .header("X-Forwarded-For", "192.168.1.10, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.amount").value(1000.00))
                .andExpect(jsonPath("$.currency").value("TRY"))
                .andExpect(jsonPath("$.orderId").value("ORDER-PAYMENT-CONTROLLER-1001"))
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.providerType").value("MOCK_BANK"))
                .andExpect(jsonPath("$.providerReferenceId").value("MOCK-BANK-REF-1001"))
                .andExpect(jsonPath("$.providerResponseCode").value("00"))
                .andExpect(jsonPath("$.providerResponseMessage").value("APPROVED"))
                .andExpect(jsonPath("$.cardLastFourDigits").value("3456"));

        ArgumentCaptor<PaymentInitiateRequest> requestCaptor =
                ArgumentCaptor.forClass(PaymentInitiateRequest.class);

        verify(paymentService).initiatePayment(
                eq("pk_live_valid_key"),
                eq("192.168.1.10"),
                requestCaptor.capture()
        );

        PaymentInitiateRequest capturedRequest = requestCaptor.getValue();

        assertThat(capturedRequest.amount()).isEqualByComparingTo("1000.00");
        assertThat(capturedRequest.currency()).isEqualTo("TRY");
        assertThat(capturedRequest.orderId()).isEqualTo("ORDER-PAYMENT-CONTROLLER-1001");
        assertThat(capturedRequest.cardToken()).isEqualTo("card_token_1234567890123456");
        assertThat(capturedRequest.providerType()).isEqualTo(PaymentProviderType.MOCK_BANK);
    }

    @Test
    void initiatePayment_shouldUseUnknownClientIp_whenForwardedForHeaderIsMissing() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        PaymentInitiateRequest request = new PaymentInitiateRequest(
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-PAYMENT-NO-IP-1001",
                "card_token_1234567890123456",
                PaymentProviderType.MOCK_BANK
        );

        PaymentResponse response = createPaymentResponse(
                paymentId,
                merchantId,
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-PAYMENT-NO-IP-1001",
                PaymentStatus.AUTHORIZED,
                PaymentProviderType.MOCK_BANK,
                "MOCK-BANK-REF-1002",
                "00",
                "APPROVED",
                "3456"
        );

        when(paymentService.initiatePayment(
                anyString(),
                anyString(),
                any(PaymentInitiateRequest.class)
        )).thenReturn(response);

        mockMvc.perform(post("/api/payments/initiate")
                        .header("X-API-Key", "pk_live_valid_key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("AUTHORIZED"));

        verify(paymentService).initiatePayment(
                eq("pk_live_valid_key"),
                eq("unknown"),
                any(PaymentInitiateRequest.class)
        );
    }

    @Test
    void initiatePayment_shouldReturnBadRequest_whenApiKeyHeaderIsMissing() throws Exception {
        PaymentInitiateRequest request = new PaymentInitiateRequest(
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-MISSING-API-KEY-1001",
                "card_token_1234567890123456",
                PaymentProviderType.MOCK_BANK
        );

        mockMvc.perform(post("/api/payments/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(paymentService, never()).initiatePayment(
                anyString(),
                anyString(),
                any(PaymentInitiateRequest.class)
        );
    }

    @Test
    void initiatePayment_shouldReturnBadRequest_whenRequestBodyIsInvalid() throws Exception {
        String invalidRequestBody = """
                {
                  "amount": 0.50,
                  "currency": "TR",
                  "orderId": "",
                  "cardToken": "",
                  "providerType": null
                }
                """;

        mockMvc.perform(post("/api/payments/initiate")
                        .header("X-API-Key", "pk_live_valid_key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.validationErrors.amount").exists())
                .andExpect(jsonPath("$.validationErrors.currency").exists())
                .andExpect(jsonPath("$.validationErrors.orderId").exists())
                .andExpect(jsonPath("$.validationErrors.cardToken").exists())
                .andExpect(jsonPath("$.validationErrors.providerType").exists());

        verify(paymentService, never()).initiatePayment(
                anyString(),
                anyString(),
                any(PaymentInitiateRequest.class)
        );
    }

    @Test
    void initiatePayment_shouldReturnUnauthorized_whenApiKeyIsInvalid() throws Exception {
        PaymentInitiateRequest request = new PaymentInitiateRequest(
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-INVALID-MERCHANT-1001",
                "card_token_1234567890123456",
                PaymentProviderType.MOCK_BANK
        );

        when(paymentService.initiatePayment(
                anyString(),
                anyString(),
                any(PaymentInitiateRequest.class)
        )).thenThrow(new InvalidMerchantApiKeyException());

        mockMvc.perform(post("/api/payments/initiate")
                        .header("X-API-Key", "invalid-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid or inactive merchant API key"));

        verify(paymentService).initiatePayment(
                eq("invalid-api-key"),
                eq("unknown"),
                any(PaymentInitiateRequest.class)
        );
    }

    @Test
    void initiatePayment_shouldReturnConflict_whenOrderAlreadyExists() throws Exception {
        UUID merchantId = UUID.randomUUID();

        PaymentInitiateRequest request = new PaymentInitiateRequest(
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-DUPLICATE-1001",
                "card_token_1234567890123456",
                PaymentProviderType.MOCK_BANK
        );

        when(paymentService.initiatePayment(
                anyString(),
                anyString(),
                any(PaymentInitiateRequest.class)
        )).thenThrow(new DuplicateOrderException(merchantId, "ORDER-DUPLICATE-1001"));

        mockMvc.perform(post("/api/payments/initiate")
                        .header("X-API-Key", "pk_live_valid_key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(
                        "Payment already exists for merchant "
                                + merchantId
                                + " and order id ORDER-DUPLICATE-1001"
                ));

        verify(paymentService).initiatePayment(
                eq("pk_live_valid_key"),
                eq("unknown"),
                any(PaymentInitiateRequest.class)
        );
    }

    @Test
    void getPaymentById_shouldReturnPaymentResponse_whenPaymentExists() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        PaymentResponse response = createPaymentResponse(
                paymentId,
                merchantId,
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-GET-PAYMENT-1001",
                PaymentStatus.AUTHORIZED,
                PaymentProviderType.MOCK_BANK,
                "MOCK-BANK-REF-1001",
                "00",
                "APPROVED",
                "3456"
        );

        when(paymentService.getPaymentById(paymentId)).thenReturn(response);

        mockMvc.perform(get("/api/payments/{paymentId}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.amount").value(1000.00))
                .andExpect(jsonPath("$.currency").value("TRY"))
                .andExpect(jsonPath("$.orderId").value("ORDER-GET-PAYMENT-1001"))
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.providerType").value("MOCK_BANK"))
                .andExpect(jsonPath("$.providerReferenceId").value("MOCK-BANK-REF-1001"))
                .andExpect(jsonPath("$.providerResponseCode").value("00"))
                .andExpect(jsonPath("$.providerResponseMessage").value("APPROVED"))
                .andExpect(jsonPath("$.cardLastFourDigits").value("3456"));

        verify(paymentService).getPaymentById(paymentId);
    }

    @Test
    void getPaymentById_shouldReturnNotFound_whenPaymentDoesNotExist() throws Exception {
        UUID paymentId = UUID.randomUUID();

        when(paymentService.getPaymentById(paymentId))
                .thenThrow(new PaymentNotFoundException(paymentId));

        mockMvc.perform(get("/api/payments/{paymentId}", paymentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Payment not found with id: " + paymentId));

        verify(paymentService).getPaymentById(paymentId);
    }

    @Test
    void getPaymentsByMerchantId_shouldReturnMerchantPaymentList() throws Exception {
        UUID merchantId = UUID.randomUUID();

        PaymentResponse firstPayment = createPaymentResponse(
                UUID.randomUUID(),
                merchantId,
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-MERCHANT-PAYMENT-1001",
                PaymentStatus.AUTHORIZED,
                PaymentProviderType.MOCK_BANK,
                "MOCK-BANK-REF-1001",
                "00",
                "APPROVED",
                "3456"
        );

        PaymentResponse secondPayment = createPaymentResponse(
                UUID.randomUUID(),
                merchantId,
                new BigDecimal("2000.00"),
                "TRY",
                "ORDER-MERCHANT-PAYMENT-1002",
                PaymentStatus.FAILED,
                PaymentProviderType.MOCK_BANK,
                null,
                "LIMIT_EXCEEDED",
                "Payment amount exceeds mock bank authorization limit",
                "3456"
        );

        when(paymentService.getPaymentsByMerchantId(merchantId))
                .thenReturn(List.of(firstPayment, secondPayment));

        mockMvc.perform(get("/api/payments/merchant/{merchantId}", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$[0].orderId").value("ORDER-MERCHANT-PAYMENT-1001"))
                .andExpect(jsonPath("$[0].status").value("AUTHORIZED"))
                .andExpect(jsonPath("$[0].providerResponseCode").value("00"))
                .andExpect(jsonPath("$[1].merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$[1].orderId").value("ORDER-MERCHANT-PAYMENT-1002"))
                .andExpect(jsonPath("$[1].status").value("FAILED"))
                .andExpect(jsonPath("$[1].providerResponseCode").value("LIMIT_EXCEEDED"));

        verify(paymentService).getPaymentsByMerchantId(merchantId);
    }

    private PaymentResponse createPaymentResponse(
            UUID id,
            UUID merchantId,
            BigDecimal amount,
            String currency,
            String orderId,
            PaymentStatus status,
            PaymentProviderType providerType,
            String providerReferenceId,
            String providerResponseCode,
            String providerResponseMessage,
            String cardLastFourDigits
    ) {
        LocalDateTime now = LocalDateTime.now();

        return new PaymentResponse(
                id,
                merchantId,
                amount,
                currency,
                orderId,
                status,
                providerType,
                providerReferenceId,
                providerResponseCode,
                providerResponseMessage,
                cardLastFourDigits,
                now,
                now
        );
    }
}