package com.paycore.notification.service;

import com.paycore.notification.domain.NotificationLog;
import com.paycore.notification.domain.NotificationStatus;
import com.paycore.notification.domain.NotificationType;
import com.paycore.notification.dto.NotificationLogResponse;
import com.paycore.notification.dto.PaymentNotificationMessage;
import com.paycore.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationLogRepository);
    }

    @Test
    void processPaymentNotification_shouldCreateNotificationLogThenMarkItAsSent() {
        PaymentNotificationMessage message = createPaymentNotificationMessage(
                "AUTHORIZED",
                "00",
                "APPROVED"
        );

        AtomicInteger saveCallCounter = new AtomicInteger(0);

        when(notificationLogRepository.save(any(NotificationLog.class)))
                .thenAnswer(invocation -> {
                    NotificationLog log = invocation.getArgument(0);
                    int callNumber = saveCallCounter.incrementAndGet();

                    if (callNumber == 1) {
                        assertThat(log.getPaymentId()).isEqualTo(message.paymentId());
                        assertThat(log.getMerchantId()).isEqualTo(message.merchantId());
                        assertThat(log.getMerchantName()).isEqualTo(message.merchantName());
                        assertThat(log.getNotificationType()).isEqualTo(NotificationType.PAYMENT_RESULT);
                        assertThat(log.getPaymentStatus()).isEqualTo("AUTHORIZED");
                        assertThat(log.getAmount()).isEqualByComparingTo("1000.00");
                        assertThat(log.getCurrency()).isEqualTo("TRY");
                        assertThat(log.getOrderId()).isEqualTo(message.orderId());
                        assertThat(log.getProviderReferenceId()).isEqualTo(message.providerReferenceId());
                        assertThat(log.getProviderResponseCode()).isEqualTo("00");
                        assertThat(log.getProviderResponseMessage()).isEqualTo("APPROVED");
                        assertThat(log.getStatus()).isEqualTo(NotificationStatus.RECEIVED);
                        assertThat(log.getSimulatedTarget()).isEqualTo("merchant-webhook://" + message.merchantId());
                        assertThat(log.getMessage()).contains("Payment AUTHORIZED");
                        assertThat(log.getMessage()).contains(message.orderId());
                        assertThat(log.getMessage()).contains("1000.00 TRY");
                        assertThat(log.getMessage()).contains("Provider response: APPROVED");
                        assertThat(log.getReceivedAt()).isNotNull();
                        assertThat(log.getSentAt()).isNull();

                        log.setId(UUID.randomUUID());
                    }

                    if (callNumber == 2) {
                        assertThat(log.getStatus()).isEqualTo(NotificationStatus.SENT);
                        assertThat(log.getSentAt()).isNotNull();
                    }

                    return log;
                });

        notificationService.processPaymentNotification(message);

        verify(notificationLogRepository, times(2)).save(any(NotificationLog.class));
        assertThat(saveCallCounter.get()).isEqualTo(2);
    }

    @Test
    void processPaymentNotification_shouldCreateFailedPaymentNotificationMessage() {
        PaymentNotificationMessage message = createPaymentNotificationMessage(
                "FAILED",
                "FRAUD_REJECTED",
                "Payment rejected due to high fraud risk"
        );

        AtomicInteger saveCallCounter = new AtomicInteger(0);

        when(notificationLogRepository.save(any(NotificationLog.class)))
                .thenAnswer(invocation -> {
                    NotificationLog log = invocation.getArgument(0);
                    int callNumber = saveCallCounter.incrementAndGet();

                    if (callNumber == 1) {
                        assertThat(log.getPaymentStatus()).isEqualTo("FAILED");
                        assertThat(log.getProviderResponseCode()).isEqualTo("FRAUD_REJECTED");
                        assertThat(log.getProviderResponseMessage()).isEqualTo("Payment rejected due to high fraud risk");
                        assertThat(log.getStatus()).isEqualTo(NotificationStatus.RECEIVED);
                        assertThat(log.getMessage()).contains("Payment FAILED");
                        assertThat(log.getMessage()).contains("Provider response: Payment rejected due to high fraud risk");

                        log.setId(UUID.randomUUID());
                    }

                    if (callNumber == 2) {
                        assertThat(log.getStatus()).isEqualTo(NotificationStatus.SENT);
                        assertThat(log.getSentAt()).isNotNull();
                    }

                    return log;
                });

        notificationService.processPaymentNotification(message);

        verify(notificationLogRepository, times(2)).save(any(NotificationLog.class));
        assertThat(saveCallCounter.get()).isEqualTo(2);
    }

    @Test
    void getNotificationsByPaymentId_shouldReturnMappedNotificationResponses() {
        UUID paymentId = UUID.randomUUID();

        NotificationLog firstLog = createNotificationLog(
                paymentId,
                UUID.randomUUID(),
                "Test Merchant",
                "AUTHORIZED",
                NotificationStatus.SENT
        );

        NotificationLog secondLog = createNotificationLog(
                paymentId,
                UUID.randomUUID(),
                "Another Merchant",
                "FAILED",
                NotificationStatus.SENT
        );

        when(notificationLogRepository.findByPaymentIdOrderByReceivedAtDesc(paymentId))
                .thenReturn(List.of(firstLog, secondLog));

        List<NotificationLogResponse> responses =
                notificationService.getNotificationsByPaymentId(paymentId);

        assertThat(responses).hasSize(2);

        assertThat(responses.get(0).id()).isEqualTo(firstLog.getId());
        assertThat(responses.get(0).paymentId()).isEqualTo(paymentId);
        assertThat(responses.get(0).merchantId()).isEqualTo(firstLog.getMerchantId());
        assertThat(responses.get(0).merchantName()).isEqualTo("Test Merchant");
        assertThat(responses.get(0).notificationType()).isEqualTo(NotificationType.PAYMENT_RESULT);
        assertThat(responses.get(0).paymentStatus()).isEqualTo("AUTHORIZED");
        assertThat(responses.get(0).amount()).isEqualByComparingTo("1000.00");
        assertThat(responses.get(0).currency()).isEqualTo("TRY");
        assertThat(responses.get(0).orderId()).isEqualTo(firstLog.getOrderId());
        assertThat(responses.get(0).providerReferenceId()).isEqualTo(firstLog.getProviderReferenceId());
        assertThat(responses.get(0).providerResponseCode()).isEqualTo("00");
        assertThat(responses.get(0).providerResponseMessage()).isEqualTo("APPROVED");
        assertThat(responses.get(0).status()).isEqualTo(NotificationStatus.SENT);
        assertThat(responses.get(0).simulatedTarget()).isEqualTo(firstLog.getSimulatedTarget());
        assertThat(responses.get(0).message()).isEqualTo(firstLog.getMessage());
        assertThat(responses.get(0).receivedAt()).isEqualTo(firstLog.getReceivedAt());
        assertThat(responses.get(0).sentAt()).isEqualTo(firstLog.getSentAt());

        assertThat(responses.get(1).paymentId()).isEqualTo(paymentId);
        assertThat(responses.get(1).merchantName()).isEqualTo("Another Merchant");
        assertThat(responses.get(1).paymentStatus()).isEqualTo("FAILED");
    }

    @Test
    void getNotificationsByMerchantId_shouldReturnMappedNotificationResponses() {
        UUID merchantId = UUID.randomUUID();

        NotificationLog firstLog = createNotificationLog(
                UUID.randomUUID(),
                merchantId,
                "Test Merchant",
                "AUTHORIZED",
                NotificationStatus.SENT
        );

        NotificationLog secondLog = createNotificationLog(
                UUID.randomUUID(),
                merchantId,
                "Test Merchant",
                "FAILED",
                NotificationStatus.SENT
        );

        when(notificationLogRepository.findByMerchantIdOrderByReceivedAtDesc(merchantId))
                .thenReturn(List.of(firstLog, secondLog));

        List<NotificationLogResponse> responses =
                notificationService.getNotificationsByMerchantId(merchantId);

        assertThat(responses).hasSize(2);

        assertThat(responses.get(0).merchantId()).isEqualTo(merchantId);
        assertThat(responses.get(0).merchantName()).isEqualTo("Test Merchant");
        assertThat(responses.get(0).paymentStatus()).isEqualTo("AUTHORIZED");
        assertThat(responses.get(0).status()).isEqualTo(NotificationStatus.SENT);

        assertThat(responses.get(1).merchantId()).isEqualTo(merchantId);
        assertThat(responses.get(1).merchantName()).isEqualTo("Test Merchant");
        assertThat(responses.get(1).paymentStatus()).isEqualTo("FAILED");
        assertThat(responses.get(1).status()).isEqualTo(NotificationStatus.SENT);
    }

    private PaymentNotificationMessage createPaymentNotificationMessage(
            String paymentStatus,
            String providerResponseCode,
            String providerResponseMessage
    ) {
        return new PaymentNotificationMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Test Merchant",
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-NOTIFICATION-1001",
                paymentStatus,
                "MOCK_BANK",
                "PROVIDER-REF-1001",
                providerResponseCode,
                providerResponseMessage,
                LocalDateTime.now()
        );
    }

    private NotificationLog createNotificationLog(
            UUID paymentId,
            UUID merchantId,
            String merchantName,
            String paymentStatus,
            NotificationStatus status
    ) {
        LocalDateTime receivedAt = LocalDateTime.now().minusSeconds(5);
        LocalDateTime sentAt = LocalDateTime.now();

        return NotificationLog.builder()
                .id(UUID.randomUUID())
                .paymentId(paymentId)
                .merchantId(merchantId)
                .merchantName(merchantName)
                .notificationType(NotificationType.PAYMENT_RESULT)
                .paymentStatus(paymentStatus)
                .amount(new BigDecimal("1000.00"))
                .currency("TRY")
                .orderId("ORDER-NOTIFICATION-1001")
                .providerReferenceId("PROVIDER-REF-1001")
                .providerResponseCode("00")
                .providerResponseMessage("APPROVED")
                .status(status)
                .simulatedTarget("merchant-webhook://" + merchantId)
                .message("Payment " + paymentStatus + " for order ORDER-NOTIFICATION-1001 with amount 1000.00 TRY. Provider response: APPROVED")
                .receivedAt(receivedAt)
                .sentAt(sentAt)
                .build();
    }
}