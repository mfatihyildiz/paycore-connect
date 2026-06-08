package com.paycore.notification.service;

import com.paycore.notification.domain.NotificationLog;
import com.paycore.notification.domain.NotificationStatus;
import com.paycore.notification.domain.NotificationType;
import com.paycore.notification.dto.NotificationLogResponse;
import com.paycore.notification.dto.PaymentNotificationMessage;
import com.paycore.notification.repository.NotificationLogRepository;
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

    @Transactional
    public void processPaymentNotification(PaymentNotificationMessage message) {
        NotificationLog log = NotificationLog.builder()
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

        NotificationLog savedLog = notificationLogRepository.save(log);

        simulateSending(savedLog);
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
        log.setStatus(NotificationStatus.SENT);
        log.setSentAt(LocalDateTime.now());
        notificationLogRepository.save(log);
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