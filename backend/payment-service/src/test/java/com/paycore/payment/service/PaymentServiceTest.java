package com.paycore.payment.service;

import com.paycore.payment.client.FraudClient;
import com.paycore.payment.client.MerchantClient;
import com.paycore.payment.domain.Payment;
import com.paycore.payment.domain.PaymentProviderType;
import com.paycore.payment.domain.PaymentStatus;
import com.paycore.payment.dto.FraudCheckRequest;
import com.paycore.payment.dto.FraudCheckResponse;
import com.paycore.payment.dto.MerchantValidationResponse;
import com.paycore.payment.dto.PaymentInitiateRequest;
import com.paycore.payment.dto.PaymentResponse;
import com.paycore.payment.event.PaymentEventProducer;
import com.paycore.payment.exception.DuplicateOrderException;
import com.paycore.payment.exception.InvalidMerchantApiKeyException;
import com.paycore.payment.exception.PaymentNotFoundException;
import com.paycore.payment.notification.PaymentNotificationProducer;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MerchantClient merchantClient;

    @Mock
    private PaymentProviderFactory paymentProviderFactory;

    @Mock
    private PaymentEventProducer paymentEventProducer;

    @Mock
    private FraudClient fraudClient;

    @Mock
    private PaymentNotificationProducer paymentNotificationProducer;

    @Mock
    private PaymentProviderClient paymentProviderClient;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository,
                merchantClient,
                paymentProviderFactory,
                paymentEventProducer,
                fraudClient,
                paymentNotificationProducer
        );
    }

    @Test
    void initiatePayment_shouldAuthorizePayment_whenMerchantIsValidFraudApprovedAndProviderApproves() {
        UUID merchantId = UUID.randomUUID();

        PaymentInitiateRequest request = createPaymentInitiateRequest(
                new BigDecimal("1000.00"),
                "try",
                "ORDER-PAYMENT-1001",
                "card_token_1234567890123456",
                PaymentProviderType.MOCK_BANK
        );

        MerchantValidationResponse merchantResponse = new MerchantValidationResponse(
                true,
                merchantId,
                "Test Merchant",
                "ACTIVE"
        );

        FraudCheckResponse fraudResponse = createFraudCheckResponse(
                merchantId,
                "APPROVED",
                "LOW",
                0,
                "Payment risk is acceptable"
        );

        ProviderPaymentResponse providerResponse = new ProviderPaymentResponse(
                true,
                "MOCK-BANK-REF-1001",
                "00",
                "APPROVED"
        );

        List<PaymentStatus> publishedStatuses = new ArrayList<>();

        when(merchantClient.validateApiKey("valid-api-key")).thenReturn(merchantResponse);
        when(paymentRepository.existsByMerchantIdAndOrderId(merchantId, request.orderId())).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> persistLikeJpa(invocation.getArgument(0)));
        when(fraudClient.checkPaymentRisk(any(FraudCheckRequest.class))).thenReturn(fraudResponse);
        when(paymentProviderFactory.getClient(PaymentProviderType.MOCK_BANK)).thenReturn(paymentProviderClient);
        when(paymentProviderClient.authorize(any(ProviderPaymentRequest.class))).thenReturn(providerResponse);

        doAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            publishedStatuses.add(payment.getStatus());
            return null;
        }).when(paymentEventProducer).publishPaymentEvent(any(Payment.class));

        PaymentResponse response = paymentService.initiatePayment(
                "valid-api-key",
                "192.168.1.10",
                request
        );

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

        assertThat(publishedStatuses).containsExactly(
                PaymentStatus.INITIATED,
                PaymentStatus.AUTHORIZED
        );

        verify(paymentRepository, times(2)).save(any(Payment.class));
        verify(paymentEventProducer, times(2)).publishPaymentEvent(any(Payment.class));
        verify(paymentNotificationProducer).publishPaymentNotification(any(Payment.class), eq("Test Merchant"));

        ArgumentCaptor<FraudCheckRequest> fraudRequestCaptor =
                ArgumentCaptor.forClass(FraudCheckRequest.class);

        verify(fraudClient).checkPaymentRisk(fraudRequestCaptor.capture());

        FraudCheckRequest capturedFraudRequest = fraudRequestCaptor.getValue();

        assertThat(capturedFraudRequest.paymentId()).isEqualTo(response.id());
        assertThat(capturedFraudRequest.merchantId()).isEqualTo(merchantId);
        assertThat(capturedFraudRequest.amount()).isEqualByComparingTo("1000.00");
        assertThat(capturedFraudRequest.currency()).isEqualTo("TRY");
        assertThat(capturedFraudRequest.orderId()).isEqualTo("ORDER-PAYMENT-1001");
        assertThat(capturedFraudRequest.cardToken()).isEqualTo("card_token_1234567890123456");
        assertThat(capturedFraudRequest.ipAddress()).isEqualTo("192.168.1.10");

        ArgumentCaptor<ProviderPaymentRequest> providerRequestCaptor =
                ArgumentCaptor.forClass(ProviderPaymentRequest.class);

        verify(paymentProviderClient).authorize(providerRequestCaptor.capture());

        ProviderPaymentRequest capturedProviderRequest = providerRequestCaptor.getValue();

        assertThat(capturedProviderRequest.merchantId()).isEqualTo(merchantId);
        assertThat(capturedProviderRequest.amount()).isEqualByComparingTo("1000.00");
        assertThat(capturedProviderRequest.currency()).isEqualTo("TRY");
        assertThat(capturedProviderRequest.orderId()).isEqualTo("ORDER-PAYMENT-1001");
        assertThat(capturedProviderRequest.cardToken()).isEqualTo("card_token_1234567890123456");
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
                "invalid-api-key",
                "192.168.1.10",
                request
        )).isInstanceOf(InvalidMerchantApiKeyException.class);

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(fraudClient, never()).checkPaymentRisk(any(FraudCheckRequest.class));
        verify(paymentProviderFactory, never()).getClient(any(PaymentProviderType.class));
        verify(paymentEventProducer, never()).publishPaymentEvent(any(Payment.class));
        verify(paymentNotificationProducer, never()).publishPaymentNotification(any(Payment.class), anyString());
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

        MerchantValidationResponse merchantResponse = new MerchantValidationResponse(
                false,
                null,
                null,
                null
        );

        when(merchantClient.validateApiKey("invalid-api-key")).thenReturn(merchantResponse);

        assertThatThrownBy(() -> paymentService.initiatePayment(
                "invalid-api-key",
                "192.168.1.10",
                request
        )).isInstanceOf(InvalidMerchantApiKeyException.class);

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(fraudClient, never()).checkPaymentRisk(any(FraudCheckRequest.class));
        verify(paymentProviderFactory, never()).getClient(any(PaymentProviderType.class));
    }

    @Test
    void initiatePayment_shouldThrowDuplicateOrderException_whenOrderAlreadyExistsForMerchant() {
        UUID merchantId = UUID.randomUUID();

        PaymentInitiateRequest request = createPaymentInitiateRequest(
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-DUPLICATE-1001",
                "card_token_1234567890123456",
                PaymentProviderType.MOCK_BANK
        );

        MerchantValidationResponse merchantResponse = new MerchantValidationResponse(
                true,
                merchantId,
                "Test Merchant",
                "ACTIVE"
        );

        when(merchantClient.validateApiKey("valid-api-key")).thenReturn(merchantResponse);
        when(paymentRepository.existsByMerchantIdAndOrderId(merchantId, request.orderId())).thenReturn(true);

        assertThatThrownBy(() -> paymentService.initiatePayment(
                "valid-api-key",
                "192.168.1.10",
                request
        )).isInstanceOf(DuplicateOrderException.class);

        verify(paymentRepository).existsByMerchantIdAndOrderId(merchantId, request.orderId());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(fraudClient, never()).checkPaymentRisk(any(FraudCheckRequest.class));
        verify(paymentProviderFactory, never()).getClient(any(PaymentProviderType.class));
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

        MerchantValidationResponse merchantResponse = new MerchantValidationResponse(
                true,
                merchantId,
                "Fraud Test Merchant",
                "ACTIVE"
        );

        FraudCheckResponse fraudResponse = createFraudCheckResponse(
                merchantId,
                "REJECTED",
                "HIGH",
                100,
                "Payment rejected due to high fraud risk"
        );

        List<PaymentStatus> publishedStatuses = new ArrayList<>();

        when(merchantClient.validateApiKey("valid-api-key")).thenReturn(merchantResponse);
        when(paymentRepository.existsByMerchantIdAndOrderId(merchantId, request.orderId())).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> persistLikeJpa(invocation.getArgument(0)));
        when(fraudClient.checkPaymentRisk(any(FraudCheckRequest.class))).thenReturn(fraudResponse);

        doAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            publishedStatuses.add(payment.getStatus());
            return null;
        }).when(paymentEventProducer).publishPaymentEvent(any(Payment.class));

        PaymentResponse response = paymentService.initiatePayment(
                "valid-api-key",
                "10.10.10.10",
                request
        );

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.providerReferenceId()).isNull();
        assertThat(response.providerResponseCode()).isEqualTo("FRAUD_REJECTED");
        assertThat(response.providerResponseMessage()).isEqualTo("Payment rejected due to high fraud risk");

        assertThat(publishedStatuses).containsExactly(
                PaymentStatus.INITIATED,
                PaymentStatus.FAILED
        );

        verify(paymentRepository, times(2)).save(any(Payment.class));
        verify(paymentEventProducer, times(2)).publishPaymentEvent(any(Payment.class));
        verify(paymentNotificationProducer).publishPaymentNotification(any(Payment.class), eq("Fraud Test Merchant"));

        verify(paymentProviderFactory, never()).getClient(any(PaymentProviderType.class));
        verify(paymentProviderClient, never()).authorize(any(ProviderPaymentRequest.class));
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

        MerchantValidationResponse merchantResponse = new MerchantValidationResponse(
                true,
                merchantId,
                "Provider Test Merchant",
                "ACTIVE"
        );

        FraudCheckResponse fraudResponse = createFraudCheckResponse(
                merchantId,
                "APPROVED",
                "LOW",
                0,
                "Payment risk is acceptable"
        );

        ProviderPaymentResponse providerResponse = new ProviderPaymentResponse(
                false,
                null,
                "LIMIT_EXCEEDED",
                "Payment amount exceeds mock bank authorization limit"
        );

        List<PaymentStatus> publishedStatuses = new ArrayList<>();

        when(merchantClient.validateApiKey("valid-api-key")).thenReturn(merchantResponse);
        when(paymentRepository.existsByMerchantIdAndOrderId(merchantId, request.orderId())).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> persistLikeJpa(invocation.getArgument(0)));
        when(fraudClient.checkPaymentRisk(any(FraudCheckRequest.class))).thenReturn(fraudResponse);
        when(paymentProviderFactory.getClient(PaymentProviderType.MOCK_BANK)).thenReturn(paymentProviderClient);
        when(paymentProviderClient.authorize(any(ProviderPaymentRequest.class))).thenReturn(providerResponse);

        doAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            publishedStatuses.add(payment.getStatus());
            return null;
        }).when(paymentEventProducer).publishPaymentEvent(any(Payment.class));

        PaymentResponse response = paymentService.initiatePayment(
                "valid-api-key",
                "192.168.1.10",
                request
        );

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.providerReferenceId()).isNull();
        assertThat(response.providerResponseCode()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(response.providerResponseMessage()).isEqualTo("Payment amount exceeds mock bank authorization limit");

        assertThat(publishedStatuses).containsExactly(
                PaymentStatus.INITIATED,
                PaymentStatus.FAILED
        );

        verify(paymentRepository, times(2)).save(any(Payment.class));
        verify(paymentEventProducer, times(2)).publishPaymentEvent(any(Payment.class));
        verify(paymentNotificationProducer).publishPaymentNotification(any(Payment.class), eq("Provider Test Merchant"));
    }

    @Test
    void initiatePayment_shouldContinueProviderFlow_whenFraudResponseIsNull() {
        UUID merchantId = UUID.randomUUID();

        PaymentInitiateRequest request = createPaymentInitiateRequest(
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-FRAUD-NULL-1001",
                "card_token_1234567890123456",
                PaymentProviderType.MOCK_BANK
        );

        MerchantValidationResponse merchantResponse = new MerchantValidationResponse(
                true,
                merchantId,
                "Test Merchant",
                "ACTIVE"
        );

        ProviderPaymentResponse providerResponse = new ProviderPaymentResponse(
                true,
                "MOCK-BANK-REF-NULL-FRAUD",
                "00",
                "APPROVED"
        );

        when(merchantClient.validateApiKey("valid-api-key")).thenReturn(merchantResponse);
        when(paymentRepository.existsByMerchantIdAndOrderId(merchantId, request.orderId())).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> persistLikeJpa(invocation.getArgument(0)));
        when(fraudClient.checkPaymentRisk(any(FraudCheckRequest.class))).thenReturn(null);
        when(paymentProviderFactory.getClient(PaymentProviderType.MOCK_BANK)).thenReturn(paymentProviderClient);
        when(paymentProviderClient.authorize(any(ProviderPaymentRequest.class))).thenReturn(providerResponse);

        PaymentResponse response = paymentService.initiatePayment(
                "valid-api-key",
                "192.168.1.10",
                request
        );

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(response.providerReferenceId()).isEqualTo("MOCK-BANK-REF-NULL-FRAUD");
        assertThat(response.providerResponseCode()).isEqualTo("00");

        verify(paymentProviderFactory).getClient(PaymentProviderType.MOCK_BANK);
        verify(paymentProviderClient).authorize(any(ProviderPaymentRequest.class));
        verify(paymentNotificationProducer).publishPaymentNotification(any(Payment.class), eq("Test Merchant"));
    }

    @Test
    void getPaymentById_shouldReturnPaymentResponse_whenPaymentExists() {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        Payment payment = createPayment(
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

        Payment firstPayment = createPayment(
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

        Payment secondPayment = createPayment(
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

        when(paymentRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId))
                .thenReturn(List.of(firstPayment, secondPayment));

        List<PaymentResponse> responses = paymentService.getPaymentsByMerchantId(merchantId);

        assertThat(responses).hasSize(2);

        assertThat(responses.get(0).merchantId()).isEqualTo(merchantId);
        assertThat(responses.get(0).status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(responses.get(0).orderId()).isEqualTo("ORDER-MERCHANT-PAYMENT-1001");

        assertThat(responses.get(1).merchantId()).isEqualTo(merchantId);
        assertThat(responses.get(1).status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(responses.get(1).orderId()).isEqualTo("ORDER-MERCHANT-PAYMENT-1002");
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

    private Payment persistLikeJpa(Payment payment) {
        if (payment.getId() == null) {
            payment.setId(UUID.randomUUID());
        }

        if (payment.getCreatedAt() == null) {
            payment.setCreatedAt(LocalDateTime.now());
        }

        payment.setUpdatedAt(LocalDateTime.now());

        return payment;
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