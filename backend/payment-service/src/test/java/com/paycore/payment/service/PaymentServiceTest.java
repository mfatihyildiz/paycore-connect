package com.paycore.payment.service;

import com.paycore.payment.client.FraudClient;
import com.paycore.payment.client.MerchantClient;
import com.paycore.payment.domain.IdempotencyOperation;
import com.paycore.payment.domain.IdempotencyRecord;
import com.paycore.payment.domain.IdempotencyStatus;
import com.paycore.payment.domain.Payment;
import com.paycore.payment.domain.PaymentProviderType;
import com.paycore.payment.domain.PaymentStatus;
import com.paycore.payment.dto.FraudCheckRequest;
import com.paycore.payment.dto.FraudCheckResponse;
import com.paycore.payment.dto.MerchantValidationResponse;
import com.paycore.payment.dto.PaymentInitiateRequest;
import com.paycore.payment.dto.PaymentResponse;
import com.paycore.payment.exception.DuplicateOrderException;
import com.paycore.payment.exception.InvalidIdempotencyKeyException;
import com.paycore.payment.exception.InvalidMerchantApiKeyException;
import com.paycore.payment.exception.PaymentNotFoundException;
import com.paycore.payment.idempotency.PaymentRequestFingerprint;
import com.paycore.payment.provider.PaymentProviderClient;
import com.paycore.payment.provider.PaymentProviderFactory;
import com.paycore.payment.provider.ProviderPaymentRequest;
import com.paycore.payment.provider.ProviderPaymentResponse;
import com.paycore.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String VALID_API_KEY = "valid-api-key";
    private static final String IDEMPOTENCY_KEY = "payment-test-key-001";
    private static final String CLIENT_IP = "192.168.1.10";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MerchantClient merchantClient;

    @Mock
    private PaymentProviderFactory paymentProviderFactory;

    @Mock
    private FraudClient fraudClient;

    @Mock
    private PaymentProviderClient paymentProviderClient;

    @Mock
    private PaymentPersistenceService paymentPersistenceService;

    @Mock
    private PaymentIdempotencyService paymentIdempotencyService;

    @Mock
    private PaymentRequestFingerprint paymentRequestFingerprint;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository,
                merchantClient,
                paymentProviderFactory,
                fraudClient,
                paymentPersistenceService,
                paymentIdempotencyService,
                paymentRequestFingerprint
        );
    }

    @Test
    void initiatePayment_shouldAuthorizePayment_whenMerchantIsValidFraudApprovedAndProviderApproves() {
        UUID merchantId = UUID.randomUUID();

        PaymentInitiateRequest request = createPaymentInitiateRequest(
                new BigDecimal("1000.00"), "try", "ORDER-PAYMENT-1001",
                "card_token_1234567890123456", PaymentProviderType.MOCK_BANK
        );

        MerchantValidationResponse merchantResponse = new MerchantValidationResponse(true, merchantId, "Test Merchant", "ACTIVE");

        FraudCheckResponse fraudResponse =
                createFraudCheckResponse(
                        merchantId,
                        "APPROVED",
                        "LOW",
                        0,
                        "Payment risk is acceptable"
                );

        ProviderPaymentResponse providerResponse =
                new ProviderPaymentResponse(
                        true,
                        "MOCK-BANK-REF-1001",
                        "00",
                        "APPROVED"
                );

        IdempotencyRecord idempotencyRecord = stubProcessingIdempotency(merchantId, request, IDEMPOTENCY_KEY);

        stubPersistenceFlow();

        when(merchantClient.validateApiKey(VALID_API_KEY)).thenReturn(merchantResponse);
        when(paymentRepository.existsByMerchantIdAndOrderId(merchantId, request.orderId())).thenReturn(false);

        when(fraudClient.checkPaymentRisk(any(FraudCheckRequest.class))).thenReturn(fraudResponse);
        when(paymentProviderFactory.getClient(PaymentProviderType.MOCK_BANK)).thenReturn(paymentProviderClient);
        when(paymentProviderClient.authorize(any(ProviderPaymentRequest.class))).thenReturn(providerResponse);

        PaymentResponse response = paymentService.initiatePayment(VALID_API_KEY, IDEMPOTENCY_KEY, CLIENT_IP, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.merchantId()).isEqualTo(merchantId);
        assertThat(response.amount()).isEqualByComparingTo("1000.00");
        assertThat(response.currency()).isEqualTo("TRY");
        assertThat(response.orderId()).isEqualTo("ORDER-PAYMENT-1001");
        assertThat(response.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(response.providerType()).isEqualTo(PaymentProviderType.MOCK_BANK);
        assertThat(response.providerReferenceId()).isEqualTo("MOCK-BANK-REF-1001");
        assertThat(response.providerResponseCode()).isEqualTo("00");
        assertThat(response.providerResponseMessage()).isEqualTo("APPROVED");
        assertThat(response.cardLastFourDigits()).isEqualTo("3456");

        verify(paymentIdempotencyService).validateKey(IDEMPOTENCY_KEY);
        verify(paymentIdempotencyService).complete(idempotencyRecord.getId(), response);

        verify(paymentRepository).existsByMerchantIdAndOrderId(merchantId, request.orderId());

        ArgumentCaptor<Payment> initiatedPaymentCaptor = ArgumentCaptor.forClass(Payment.class);

        verify(paymentPersistenceService).createInitiatedPayment(initiatedPaymentCaptor.capture());

        Payment initiatedPayment = initiatedPaymentCaptor.getValue();

        assertThat(initiatedPayment.getMerchantId()).isEqualTo(merchantId);
        assertThat(initiatedPayment.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(initiatedPayment.getCurrency()).isEqualTo("TRY");
        assertThat(initiatedPayment.getOrderId()).isEqualTo("ORDER-PAYMENT-1001");
        assertThat(initiatedPayment.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(initiatedPayment.getProviderType()).isEqualTo(PaymentProviderType.MOCK_BANK);
        assertThat(initiatedPayment.getCardLastFourDigits()).isEqualTo("3456");

        verify(paymentPersistenceService).finalizePayment(
                response.id(),
                PaymentStatus.AUTHORIZED,
                "MOCK-BANK-REF-1001",
                "00",
                "APPROVED",
                "Test Merchant"
        );

        ArgumentCaptor<FraudCheckRequest> fraudRequestCaptor = ArgumentCaptor.forClass(FraudCheckRequest.class);

        verify(fraudClient).checkPaymentRisk(fraudRequestCaptor.capture());

        FraudCheckRequest capturedFraudRequest = fraudRequestCaptor.getValue();

        assertThat(capturedFraudRequest.paymentId()).isEqualTo(response.id());
        assertThat(capturedFraudRequest.merchantId()).isEqualTo(merchantId);
        assertThat(capturedFraudRequest.amount()).isEqualByComparingTo("1000.00");
        assertThat(capturedFraudRequest.currency()).isEqualTo("TRY");
        assertThat(capturedFraudRequest.orderId()).isEqualTo("ORDER-PAYMENT-1001");
        assertThat(capturedFraudRequest.cardToken()).isEqualTo("card_token_1234567890123456");
        assertThat(capturedFraudRequest.ipAddress()).isEqualTo(CLIENT_IP);

        ArgumentCaptor<ProviderPaymentRequest> providerRequestCaptor = ArgumentCaptor.forClass(ProviderPaymentRequest.class);

        verify(paymentProviderClient).authorize(providerRequestCaptor.capture());

        ProviderPaymentRequest capturedProviderRequest = providerRequestCaptor.getValue();

        assertThat(capturedProviderRequest.merchantId()).isEqualTo(merchantId);
        assertThat(capturedProviderRequest.amount()).isEqualByComparingTo("1000.00");
        assertThat(capturedProviderRequest.currency()).isEqualTo("TRY");
        assertThat(capturedProviderRequest.orderId()).isEqualTo("ORDER-PAYMENT-1001");
        assertThat(capturedProviderRequest.cardToken()).isEqualTo("card_token_1234567890123456");
    }

    @Test
    void initiatePayment_shouldReplayPreviousResponse_whenIdempotentRequestWasAlreadyCompleted() {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        PaymentInitiateRequest request = createPaymentInitiateRequest(
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-IDEMPOTENT-REPLAY-1001",
                "card_token_1234567890123456",
                PaymentProviderType.MOCK_BANK
        );

        MerchantValidationResponse merchantResponse = new MerchantValidationResponse(true, merchantId, "Test Merchant", "ACTIVE");

        String requestHash = "completed-request-hash";

        IdempotencyRecord completedRecord =
                IdempotencyRecord.builder()
                        .id(UUID.randomUUID())
                        .merchantId(merchantId)
                        .operation(IdempotencyOperation.PAYMENT_INITIATION)
                        .idempotencyKey(IDEMPOTENCY_KEY)
                        .requestHash(requestHash)
                        .status(IdempotencyStatus.COMPLETED)
                        .createdAt(LocalDateTime.now())
                        .completedAt(LocalDateTime.now())
                        .build();

        PaymentResponse previousResponse =
                new PaymentResponse(
                        paymentId,
                        merchantId,
                        new BigDecimal("1000.00"),
                        "TRY",
                        "ORDER-IDEMPOTENT-REPLAY-1001",
                        PaymentStatus.AUTHORIZED,
                        PaymentProviderType.MOCK_BANK,
                        "MOCK-BANK-REF-REPLAY",
                        "00",
                        "APPROVED",
                        "3456",
                        LocalDateTime.now(),
                        LocalDateTime.now()
                );

        when(merchantClient.validateApiKey(VALID_API_KEY)).thenReturn(merchantResponse);
        when(paymentRequestFingerprint.calculate(request)).thenReturn(requestHash);
        when(paymentIdempotencyService.acquire(
                merchantId, IdempotencyOperation.PAYMENT_INITIATION, IDEMPOTENCY_KEY, requestHash
        )).thenReturn(completedRecord);
        when(paymentIdempotencyService.replay(completedRecord)).thenReturn(previousResponse);

        PaymentResponse response = paymentService.initiatePayment(VALID_API_KEY, IDEMPOTENCY_KEY, CLIENT_IP, request);

        assertThat(response).isSameAs(previousResponse);
        assertThat(response.id()).isEqualTo(paymentId);

        verify(paymentIdempotencyService).replay(completedRecord);
        verify(paymentIdempotencyService, never()).complete(any(), any());

        verifyNoInteractions(paymentRepository, paymentPersistenceService, fraudClient, paymentProviderFactory, paymentProviderClient);
    }

    @Test
    void initiatePayment_shouldThrowInvalidIdempotencyKeyException_beforeMerchantValidation() {
        PaymentInitiateRequest request = createPaymentInitiateRequest(
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-INVALID-IDEMPOTENCY-1001",
                "card_token_1234567890123456",
                PaymentProviderType.MOCK_BANK
        );

        doThrow(new InvalidIdempotencyKeyException("Idempotency-Key must not be blank"))
                .when(paymentIdempotencyService).validateKey("");

        assertThatThrownBy(() ->
                paymentService.initiatePayment(VALID_API_KEY, "", CLIENT_IP, request)
        ).isInstanceOf(InvalidIdempotencyKeyException.class);

        verify(merchantClient, never()).validateApiKey(anyString());
        verify(paymentRequestFingerprint, never()).calculate(any());
        verify(paymentIdempotencyService, never()).acquire(any(), any(), anyString(), anyString());
        verifyNoInteractions(paymentRepository, paymentPersistenceService, fraudClient, paymentProviderFactory, paymentProviderClient);
    }

    @Test
    void initiatePayment_shouldThrowInvalidMerchantApiKeyException_whenMerchantValidationReturnsNull() {
        PaymentInitiateRequest request = createPaymentInitiateRequest(
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-INVALID-KEY-1001",
                "card_token_1234567890123456",
                PaymentProviderType.MOCK_BANK
        );

        when(merchantClient.validateApiKey("invalid-api-key")).thenReturn(null);

        assertThatThrownBy(() -> paymentService.initiatePayment(
                "invalid-api-key", IDEMPOTENCY_KEY, CLIENT_IP, request)
        ).isInstanceOf(InvalidMerchantApiKeyException.class);

        verify(paymentIdempotencyService).validateKey(IDEMPOTENCY_KEY);
        verify(paymentRequestFingerprint, never()).calculate(any());
        verify(paymentIdempotencyService, never()).acquire(any(), any(), anyString(), anyString());

        verifyNoInteractions(paymentRepository, paymentPersistenceService, fraudClient, paymentProviderFactory, paymentProviderClient);
    }

    @Test
    void initiatePayment_shouldThrowInvalidMerchantApiKeyException_whenMerchantIsInvalid() {
        PaymentInitiateRequest request = createPaymentInitiateRequest(
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-INVALID-MERCHANT-1001",
                "card_token_1234567890123456",
                PaymentProviderType.MOCK_BANK
        );

        MerchantValidationResponse merchantResponse = new MerchantValidationResponse(false, null, null, null);

        when(merchantClient.validateApiKey("invalid-api-key")).thenReturn(merchantResponse);

        assertThatThrownBy(() -> paymentService.initiatePayment(
                "invalid-api-key", IDEMPOTENCY_KEY, CLIENT_IP, request)
        ).isInstanceOf(InvalidMerchantApiKeyException.class);

        verify(paymentRequestFingerprint, never()).calculate(any());
        verify(paymentIdempotencyService, never()).acquire(any(), any(), anyString(), anyString());

        verifyNoInteractions(paymentRepository, paymentPersistenceService, fraudClient, paymentProviderFactory, paymentProviderClient);
    }

    @Test
    void initiatePayment_shouldThrowDuplicateOrderExceptionAndReleaseIdempotency_whenOrderAlreadyExistsForMerchant() {
        UUID merchantId = UUID.randomUUID();

        PaymentInitiateRequest request = createPaymentInitiateRequest(
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-DUPLICATE-1001",
                "card_token_1234567890123456",
                PaymentProviderType.MOCK_BANK
        );

        MerchantValidationResponse merchantResponse =
                new MerchantValidationResponse(true, merchantId, "Test Merchant", "ACTIVE");

        IdempotencyRecord idempotencyRecord = stubProcessingIdempotency(merchantId, request, IDEMPOTENCY_KEY);

        when(merchantClient.validateApiKey(VALID_API_KEY)).thenReturn(merchantResponse);
        when(paymentRepository.existsByMerchantIdAndOrderId(merchantId, request.orderId())).thenReturn(true);

        assertThatThrownBy(() -> paymentService.initiatePayment(
                VALID_API_KEY, IDEMPOTENCY_KEY, CLIENT_IP, request)
        ).isInstanceOf(DuplicateOrderException.class);

        verify(paymentRepository).existsByMerchantIdAndOrderId(merchantId, request.orderId());
        verify(paymentIdempotencyService).release(idempotencyRecord.getId());
        verify(paymentIdempotencyService, never()).complete(any(), any());

        verifyNoInteractions(paymentPersistenceService, fraudClient, paymentProviderFactory, paymentProviderClient);
    }

    @Test
    void initiatePayment_shouldReleaseIdempotency_whenDatabaseDetectsDuplicateOrderAfterPreCheck() {
        UUID merchantId = UUID.randomUUID();

        PaymentInitiateRequest request = createPaymentInitiateRequest(
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-DUPLICATE-RACE-1001",
                "card_token_1234567890123456",
                PaymentProviderType.MOCK_BANK
        );

        MerchantValidationResponse merchantResponse =
                new MerchantValidationResponse(true, merchantId, "Test Merchant", "ACTIVE");

        IdempotencyRecord idempotencyRecord = stubProcessingIdempotency(merchantId, request, IDEMPOTENCY_KEY);

        when(merchantClient.validateApiKey(VALID_API_KEY)).thenReturn(merchantResponse);

        when(paymentRepository.existsByMerchantIdAndOrderId(merchantId, request.orderId())).thenReturn(false);

        when(paymentPersistenceService.createInitiatedPayment(any(Payment.class))).thenThrow(
                new DuplicateOrderException(merchantId, request.orderId()));

        assertThatThrownBy(() -> paymentService.initiatePayment(
                VALID_API_KEY, IDEMPOTENCY_KEY, CLIENT_IP, request)
        ).isInstanceOf(DuplicateOrderException.class);

        verify(paymentPersistenceService).createInitiatedPayment(any(Payment.class));
        verify(paymentIdempotencyService).release(idempotencyRecord.getId());
        verify(paymentIdempotencyService, never()).complete(any(), any());

        verifyNoInteractions(fraudClient, paymentProviderFactory, paymentProviderClient);
    }

    @Test
    void initiatePayment_shouldFailPaymentAndSkipProvider_whenFraudDecisionIsRejected() {
        UUID merchantId = UUID.randomUUID();

        PaymentInitiateRequest request = createPaymentInitiateRequest(
                new BigDecimal("90000.00"),
                "TRY",
                "ORDER-FRAUD-REJECTED-1001",
                "card_token_1234567890123456",
                PaymentProviderType.MOCK_BANK
        );

        MerchantValidationResponse merchantResponse =
                new MerchantValidationResponse(true, merchantId, "Fraud Test Merchant", "ACTIVE");

        FraudCheckResponse fraudResponse = createFraudCheckResponse(
                merchantId, "REJECTED", "HIGH", 100, "Payment rejected due to high fraud risk"
        );

        IdempotencyRecord idempotencyRecord = stubProcessingIdempotency(merchantId, request, IDEMPOTENCY_KEY);

        stubPersistenceFlow();

        when(merchantClient.validateApiKey(VALID_API_KEY)).thenReturn(merchantResponse);
        when(paymentRepository.existsByMerchantIdAndOrderId(merchantId, request.orderId())).thenReturn(false);
        when(fraudClient.checkPaymentRisk(any(FraudCheckRequest.class))).thenReturn(fraudResponse);

        PaymentResponse response = paymentService.initiatePayment(VALID_API_KEY, IDEMPOTENCY_KEY, "10.10.10.10", request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.providerReferenceId()).isNull();
        assertThat(response.providerResponseCode()).isEqualTo("FRAUD_REJECTED");
        assertThat(response.providerResponseMessage()).isEqualTo("Payment rejected due to high fraud risk");
        verify(paymentIdempotencyService).complete(idempotencyRecord.getId(), response);

        verify(paymentPersistenceService).createInitiatedPayment(any(Payment.class));

        verify(paymentPersistenceService).finalizePayment(
                response.id(),
                PaymentStatus.FAILED,
                null,
                "FRAUD_REJECTED",
                "Payment rejected due to high fraud risk",
                "Fraud Test Merchant"
        );

        verifyNoInteractions(paymentProviderFactory, paymentProviderClient);
    }

    @Test
    void initiatePayment_shouldFailPayment_whenProviderRejectsPayment() {
        UUID merchantId = UUID.randomUUID();

        PaymentInitiateRequest request = createPaymentInitiateRequest(
                new BigDecimal("150000.00"),
                "TRY",
                "ORDER-PROVIDER-FAILED-1001",
                "card_token_1234567890123456",
                PaymentProviderType.MOCK_BANK
        );

        MerchantValidationResponse merchantResponse =
                new MerchantValidationResponse(true, merchantId, "Provider Test Merchant", "ACTIVE");

        FraudCheckResponse fraudResponse = createFraudCheckResponse(
                merchantId, "APPROVED", "LOW", 0, "Payment risk is acceptable"
        );

        ProviderPaymentResponse providerResponse = new ProviderPaymentResponse(
                false,
                null,
                "LIMIT_EXCEEDED",
                "Payment amount exceeds mock bank authorization limit"
        );

        IdempotencyRecord idempotencyRecord = stubProcessingIdempotency(merchantId, request, IDEMPOTENCY_KEY);

        stubPersistenceFlow();

        when(merchantClient.validateApiKey(VALID_API_KEY)).thenReturn(merchantResponse);
        when(paymentRepository.existsByMerchantIdAndOrderId(merchantId, request.orderId())).thenReturn(false);

        when(fraudClient.checkPaymentRisk(any(FraudCheckRequest.class))).thenReturn(fraudResponse);
        when(paymentProviderFactory.getClient(PaymentProviderType.MOCK_BANK)).thenReturn(paymentProviderClient);
        when(paymentProviderClient.authorize(any(ProviderPaymentRequest.class))).thenReturn(providerResponse);

        PaymentResponse response =
                paymentService.initiatePayment(VALID_API_KEY, IDEMPOTENCY_KEY, CLIENT_IP, request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.providerReferenceId()).isNull();
        assertThat(response.providerResponseCode()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(response.providerResponseMessage()).isEqualTo("Payment amount exceeds mock bank authorization limit");

        verify(paymentIdempotencyService).complete(idempotencyRecord.getId(), response);
        verify(paymentPersistenceService).createInitiatedPayment(any(Payment.class));
        verify(paymentPersistenceService).finalizePayment(
                response.id(),
                PaymentStatus.FAILED,
                null,
                "LIMIT_EXCEEDED",
                "Payment amount exceeds mock bank authorization limit",
                "Provider Test Merchant"
        );
    }

    @Test
    void initiatePayment_shouldContinueProviderFlow_whenFraudResponseIsNull() {
        UUID merchantId = UUID.randomUUID();

        PaymentInitiateRequest request =
                createPaymentInitiateRequest(
                        new BigDecimal("1000.00"),
                        "TRY",
                        "ORDER-FRAUD-NULL-1001",
                        "card_token_1234567890123456",
                        PaymentProviderType.MOCK_BANK
                );

        MerchantValidationResponse merchantResponse = new MerchantValidationResponse(true, merchantId, "Test Merchant", "ACTIVE");

        ProviderPaymentResponse providerResponse = new ProviderPaymentResponse(true, "MOCK-BANK-REF-NULL-FRAUD", "00", "APPROVED");

        IdempotencyRecord idempotencyRecord = stubProcessingIdempotency(merchantId, request, IDEMPOTENCY_KEY);

        stubPersistenceFlow();
        when(merchantClient.validateApiKey(VALID_API_KEY)).thenReturn(merchantResponse);
        when(paymentRepository.existsByMerchantIdAndOrderId(merchantId, request.orderId())).thenReturn(false);

        when(fraudClient.checkPaymentRisk(any(FraudCheckRequest.class))).thenReturn(null);
        when(paymentProviderFactory.getClient(PaymentProviderType.MOCK_BANK)).thenReturn(paymentProviderClient);
        when(paymentProviderClient.authorize(any(ProviderPaymentRequest.class))).thenReturn(providerResponse);

        PaymentResponse response = paymentService.initiatePayment(VALID_API_KEY, IDEMPOTENCY_KEY, CLIENT_IP, request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(response.providerReferenceId()).isEqualTo("MOCK-BANK-REF-NULL-FRAUD");
        assertThat(response.providerResponseCode()).isEqualTo("00");

        verify(paymentIdempotencyService).complete(idempotencyRecord.getId(), response);
        verify(paymentProviderFactory).getClient(PaymentProviderType.MOCK_BANK);
        verify(paymentProviderClient).authorize(any(ProviderPaymentRequest.class));

        verify(paymentPersistenceService).finalizePayment(
                response.id(),
                PaymentStatus.AUTHORIZED,
                "MOCK-BANK-REF-NULL-FRAUD",
                "00",
                "APPROVED",
                "Test Merchant"
        );
    }

    @Test
    void getPaymentById_shouldReturnPaymentResponse_whenPaymentExists() {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        Payment payment =
                createPayment(
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

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentById(paymentId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(paymentId);
        assertThat(response.merchantId()).isEqualTo(merchantId);
        assertThat(response.amount()).isEqualByComparingTo("1000.00");
        assertThat(response.currency()).isEqualTo("TRY");
        assertThat(response.orderId()).isEqualTo("ORDER-GET-PAYMENT-1001");
        assertThat(response.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(response.providerType()).isEqualTo(PaymentProviderType.MOCK_BANK);
        assertThat(response.providerReferenceId()).isEqualTo("MOCK-BANK-REF-1001");
        assertThat(response.providerResponseCode()).isEqualTo("00");
        assertThat(response.providerResponseMessage()).isEqualTo("APPROVED");
        assertThat(response.cardLastFourDigits()).isEqualTo("3456");
    }

    @Test
    void getPaymentById_shouldThrowPaymentNotFoundException_whenPaymentDoesNotExist() {
        UUID paymentId = UUID.randomUUID();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentById(paymentId))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(paymentId.toString());
    }

    @Test
    void getPaymentsByMerchantId_shouldReturnPaymentResponses() {
        UUID merchantId = UUID.randomUUID();

        Payment firstPayment =
                createPayment(
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

        Payment secondPayment =
                createPayment(
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

        when(paymentRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId)).thenReturn(List.of(firstPayment, secondPayment));

        List<PaymentResponse> responses = paymentService.getPaymentsByMerchantId(merchantId);

        assertThat(responses).hasSize(2);

        assertThat(responses.get(0).merchantId()).isEqualTo(merchantId);
        assertThat(responses.get(0).status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(responses.get(0).orderId()).isEqualTo("ORDER-MERCHANT-PAYMENT-1001");

        assertThat(responses.get(1).merchantId()).isEqualTo(merchantId);
        assertThat(responses.get(1).status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(responses.get(1).orderId()).isEqualTo("ORDER-MERCHANT-PAYMENT-1002");
    }

    private void stubPersistenceFlow() {
        AtomicReference<Payment> paymentState = new AtomicReference<>();

        when(paymentPersistenceService.createInitiatedPayment(
                any(Payment.class)
        )).thenAnswer(invocation -> {
            Payment payment = persistInitiatedLikeJpa(invocation.getArgument(0));

            paymentState.set(payment);
            return payment;
        });

        when(paymentPersistenceService.finalizePayment(
                any(UUID.class),
                any(PaymentStatus.class),
                nullable(String.class),
                nullable(String.class),
                nullable(String.class),
                anyString()
        )).thenAnswer(invocation -> {
            Payment payment = paymentState.get();

            payment.setStatus(invocation.getArgument(1));
            payment.setProviderReferenceId(invocation.getArgument(2));
            payment.setProviderResponseCode(invocation.getArgument(3));
            payment.setProviderResponseMessage(invocation.getArgument(4));
            payment.setUpdatedAt(LocalDateTime.now());

            return payment;
        });
    }

    private IdempotencyRecord stubProcessingIdempotency(
            UUID merchantId,
            PaymentInitiateRequest request,
            String idempotencyKey
    ) {
        String requestHash = "request-hash-" + request.orderId();

        IdempotencyRecord record =
                IdempotencyRecord.builder()
                        .id(UUID.randomUUID())
                        .merchantId(merchantId)
                        .operation(IdempotencyOperation.PAYMENT_INITIATION)
                        .idempotencyKey(idempotencyKey)
                        .requestHash(requestHash)
                        .status(IdempotencyStatus.PROCESSING)
                        .createdAt(LocalDateTime.now())
                        .build();

        when(paymentRequestFingerprint.calculate(request)).thenReturn(requestHash);

        when(paymentIdempotencyService.acquire(
                merchantId,
                IdempotencyOperation.PAYMENT_INITIATION,
                idempotencyKey,
                requestHash
        )).thenReturn(record);

        return record;
    }

    private PaymentInitiateRequest createPaymentInitiateRequest(
            BigDecimal amount,
            String currency,
            String orderId,
            String cardToken,
            PaymentProviderType providerType
    ) {
        return new PaymentInitiateRequest(
                amount,
                currency,
                orderId,
                cardToken,
                providerType
        );
    }

    private FraudCheckResponse createFraudCheckResponse(
            UUID merchantId,
            String decision,
            String riskLevel,
            int riskScore,
            String message
    ) {
        return new FraudCheckResponse(
                "fraud-check-1",
                UUID.randomUUID(),
                merchantId,
                riskScore,
                riskLevel,
                decision,
                List.of(),
                message,
                LocalDateTime.now()
        );
    }

    private Payment persistInitiatedLikeJpa(Payment payment) {
        LocalDateTime now = LocalDateTime.now();

        return Payment.builder()
                .id(payment.getId() != null ? payment.getId() : UUID.randomUUID())
                .merchantId(payment.getMerchantId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .orderId(payment.getOrderId())
                .status(payment.getStatus())
                .providerType(payment.getProviderType())
                .providerReferenceId(payment.getProviderReferenceId())
                .providerResponseCode(payment.getProviderResponseCode())
                .providerResponseMessage(payment.getProviderResponseMessage())
                .cardLastFourDigits(payment.getCardLastFourDigits())
                .createdAt(payment.getCreatedAt() != null ? payment.getCreatedAt() : now)
                .updatedAt(now)
                .build();
    }

    private Payment createPayment(
            UUID paymentId,
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

        return Payment.builder()
                .id(paymentId)
                .merchantId(merchantId)
                .amount(amount)
                .currency(currency)
                .orderId(orderId)
                .status(status)
                .providerType(providerType)
                .providerReferenceId(providerReferenceId)
                .providerResponseCode(providerResponseCode)
                .providerResponseMessage(providerResponseMessage)
                .cardLastFourDigits(cardLastFourDigits)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}