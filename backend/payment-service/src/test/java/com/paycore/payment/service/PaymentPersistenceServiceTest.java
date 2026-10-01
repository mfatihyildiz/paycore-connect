package com.paycore.payment.service;

import com.paycore.payment.domain.Payment;
import com.paycore.payment.domain.PaymentProviderType;
import com.paycore.payment.domain.PaymentStatus;
import com.paycore.payment.exception.DuplicateOrderException;
import com.paycore.payment.exception.PaymentNotFoundException;
import com.paycore.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentPersistenceServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentOutboxService paymentOutboxService;

    private PaymentPersistenceService paymentPersistenceService;

    @BeforeEach
    void setUp() {
        paymentPersistenceService = new PaymentPersistenceService(paymentRepository, paymentOutboxService);
    }

    @Test
    void shouldPersistInitiatedPaymentAndCreateOutboxEvent() {
        Payment payment = createPayment(null, PaymentStatus.INITIATED);

        when(paymentRepository.saveAndFlush(payment))
                .thenAnswer(invocation -> {
                    Payment savedPayment = invocation.getArgument(0);
                    savedPayment.setId(UUID.randomUUID());
                    return savedPayment;
                });

        Payment result = paymentPersistenceService.createInitiatedPayment(payment);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.INITIATED);

        verify(paymentRepository).saveAndFlush(payment);
        verify(paymentOutboxService).enqueuePaymentEvent(result);
    }

    @Test
    void shouldTranslateDatabaseConstraintViolationToDuplicateOrderException() {
        Payment payment = createPayment(null, PaymentStatus.INITIATED);

        when(paymentRepository.saveAndFlush(payment)).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> paymentPersistenceService.createInitiatedPayment(payment))
                .isInstanceOf(DuplicateOrderException.class)
                .hasMessageContaining(payment.getMerchantId().toString())
                .hasMessageContaining(payment.getOrderId());

        verifyNoInteractions(paymentOutboxService);
    }

    @Test
    void shouldFinalizePaymentAndCreateEventAndNotificationOutboxRecords() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = createPayment(paymentId, PaymentStatus.INITIATED);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.saveAndFlush(payment)).thenReturn(payment);

        Payment result = paymentPersistenceService.finalizePayment(
                paymentId,
                PaymentStatus.AUTHORIZED,
                "MOCK-BANK-REF-001",
                "00",
                "APPROVED",
                "Test Merchant"
        );

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(result.getProviderReferenceId()).isEqualTo("MOCK-BANK-REF-001");
        assertThat(result.getProviderResponseCode()).isEqualTo("00");
        assertThat(result.getProviderResponseMessage()).isEqualTo("APPROVED");

        verify(paymentRepository).findById(paymentId);
        verify(paymentRepository).saveAndFlush(payment);
        verify(paymentOutboxService).enqueuePaymentEvent(payment);
        verify(paymentOutboxService).enqueuePaymentNotification(payment, "Test Merchant");
    }

    @Test
    void shouldFinalizeFraudRejectedPayment() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = createPayment(paymentId, PaymentStatus.INITIATED);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.saveAndFlush(payment)).thenReturn(payment);

        Payment result = paymentPersistenceService.finalizePayment(
                paymentId,
                PaymentStatus.FAILED,
                null,
                "FRAUD_REJECTED",
                "Payment rejected by fraud rules",
                "Test Merchant"
        );

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getProviderReferenceId()).isNull();
        assertThat(result.getProviderResponseCode()).isEqualTo("FRAUD_REJECTED");
        assertThat(result.getProviderResponseMessage()).isEqualTo("Payment rejected by fraud rules");

        verify(paymentOutboxService).enqueuePaymentEvent(payment);
        verify(paymentOutboxService).enqueuePaymentNotification(payment, "Test Merchant");
    }

    @Test
    void shouldThrowPaymentNotFoundWhenFinalizingMissingPayment() {
        UUID paymentId = UUID.randomUUID();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentPersistenceService.finalizePayment(
                paymentId,
                PaymentStatus.AUTHORIZED,
                "REF-001",
                "00",
                "APPROVED",
                "Test Merchant"
        ))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(paymentId.toString());

        verify(paymentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(paymentOutboxService);
    }
    @Test
    void createInitiatedPayment_shouldNotTranslateOutboxIntegrityFailureToDuplicateOrder() {
        Payment payment = Payment.builder().id(UUID.randomUUID()).merchantId(UUID.randomUUID()).orderId("ORDER-1").build();

        when(paymentRepository.saveAndFlush(payment)).thenReturn(payment);
        DataIntegrityViolationException outboxFailure = new DataIntegrityViolationException("outbox constraint violation");
        doThrow(outboxFailure).when(paymentOutboxService).enqueuePaymentEvent(payment);
        assertThatThrownBy(() -> paymentPersistenceService.createInitiatedPayment(payment)).isSameAs(outboxFailure);
    }


    private Payment createPayment(UUID id, PaymentStatus status) {
        return Payment.builder()
                .id(id)
                .merchantId(UUID.randomUUID())
                .amount(new BigDecimal("1000.00"))
                .currency("TRY")
                .status(status)
                .providerType(PaymentProviderType.MOCK_BANK)
                .orderId("ORDER-OUTBOX-001")
                .cardLastFourDigits("3456")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
