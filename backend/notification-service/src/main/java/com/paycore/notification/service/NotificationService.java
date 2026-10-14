package com.paycore.notification.service;

import com.paycore.notification.domain.NotificationLog;
import com.paycore.notification.domain.NotificationStatus;
import com.paycore.notification.domain.NotificationType;
import com.paycore.notification.dto.NotificationLogResponse;
import com.paycore.notification.dto.PaymentNotificationMessage;
import com.paycore.notification.repository.NotificationLogRepository;
import com.paycore.notification.repository.NotificationLogWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationLogWriter notificationLogWriter;

    @Transactional
    public void processPaymentNotification(PaymentNotificationMessage message) {
        NotificationLog log = NotificationLog.builder()
                .id(UUID.randomUUID())
                .notificationId(message.notificationId())
                .paymentId(message.paymentId())
                .merchantId(message.merchantId())
                .merchantName(message.merchantName())
                .notificationType(NotificationType.PAYMENT_RESULT)
                .paymentStatus(message.paymentStatus())
                .amount(message.amount())
                .currency(message.currency())
                .orderId(message.orderId())
                .providerReferenceId(message.providerReferenceId())
                .providerResponseCode(message.providerResponseCode())
                .providerResponseMessage(message.providerResponseMessage())
                .status(NotificationStatus.RECEIVED)
                .simulatedTarget("merchant-webhook://" + message.merchantId())
                .message(buildNotificationMessage(message))
                .receivedAt(LocalDateTime.now())
                .build();

        boolean inserted = notificationLogWriter.insertIfAbsent(log);

        if (!inserted) {
            return;
        }

        simulateSending(log);
    }

    @Transactional(readOnly = true)
    public List<NotificationLogResponse> getNotificationsByPaymentId(UUID paymentId) {
        return notificationLogRepository.findByPaymentIdOrderByReceivedAtDesc(paymentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationLogResponse> getNotificationsByMerchantId(UUID merchantId) {
        return notificationLogRepository.findByMerchantIdOrderByReceivedAtDesc(merchantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void simulateSending(NotificationLog log) {
        LocalDateTime sentAt = LocalDateTime.now();

        boolean updated = notificationLogWriter.markSent(log.getId(), sentAt);

        if (!updated) {
            throw new IllegalStateException("Notification log could not be marked as SENT: " + log.getId());
        }
    }

    private String buildNotificationMessage(PaymentNotificationMessage message) {
        return "Payment "
                + message.paymentStatus()
                + " for order "
                + message.orderId()
                + " with amount "
                + message.amount()
                + " "
                + message.currency()
                + ". Provider response: "
                + message.providerResponseMessage();
    }

    private NotificationLogResponse toResponse(NotificationLog log) {
        return new NotificationLogResponse(
                log.getId(),
                log.getPaymentId(),
                log.getMerchantId(),
                log.getMerchantName(),
                log.getNotificationType(),
                log.getPaymentStatus(),
                log.getAmount(),
                log.getCurrency(),
                log.getOrderId(),
                log.getProviderReferenceId(),
                log.getProviderResponseCode(),
                log.getProviderResponseMessage(),
                log.getStatus(),
                log.getSimulatedTarget(),
                log.getMessage(),
                log.getReceivedAt(),
                log.getSentAt()
        );
    }
}