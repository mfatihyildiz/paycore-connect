package com.paycore.notification.repository;

import com.paycore.notification.domain.NotificationLog;
import com.paycore.notification.domain.NotificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NotificationLogWriter {

    private static final String INSERT_IF_ABSENT_SQL = """
            INSERT INTO notification_logs (
                id,
                notification_id,
                payment_id,
                merchant_id,
                merchant_name,
                notification_type,
                payment_status,
                amount,
                currency,
                order_id,
                provider_reference_id,
                provider_response_code,
                provider_response_message,
                status,
                simulated_target,
                message,
                received_at,
                sent_at
            )
            VALUES (
                :id,
                :notificationId,
                :paymentId,
                :merchantId,
                :merchantName,
                :notificationType,
                :paymentStatus,
                :amount,
                :currency,
                :orderId,
                :providerReferenceId,
                :providerResponseCode,
                :providerResponseMessage,
                :status,
                :simulatedTarget,
                :message,
                :receivedAt,
                :sentAt
            )
            ON CONFLICT (notification_id) DO NOTHING
            """;

    private static final String MARK_SENT_SQL = """
            UPDATE notification_logs
            SET status = :status,
                sent_at = :sentAt
            WHERE id = :id
              AND status = 'RECEIVED'
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public boolean insertIfAbsent(NotificationLog log) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", log.getId())
                .addValue("notificationId", log.getNotificationId())
                .addValue("paymentId", log.getPaymentId())
                .addValue("merchantId", log.getMerchantId())
                .addValue("merchantName", log.getMerchantName())
                .addValue("notificationType", log.getNotificationType().name())
                .addValue("paymentStatus", log.getPaymentStatus())
                .addValue("amount", log.getAmount())
                .addValue("currency", log.getCurrency())
                .addValue("orderId", log.getOrderId())
                .addValue("providerReferenceId", log.getProviderReferenceId())
                .addValue("providerResponseCode", log.getProviderResponseCode())
                .addValue("providerResponseMessage", log.getProviderResponseMessage())
                .addValue("status", log.getStatus().name())
                .addValue("simulatedTarget", log.getSimulatedTarget())
                .addValue("message", log.getMessage())
                .addValue("receivedAt", log.getReceivedAt())
                .addValue("sentAt", log.getSentAt());

        return jdbcTemplate.update(INSERT_IF_ABSENT_SQL, params) == 1;
    }

    public boolean markSent(UUID id, LocalDateTime sentAt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", NotificationStatus.SENT.name())
                .addValue("sentAt", sentAt);

        return jdbcTemplate.update(MARK_SENT_SQL, params) == 1;
    }
}
