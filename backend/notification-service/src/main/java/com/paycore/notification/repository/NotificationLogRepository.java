package com.paycore.notification.repository;

import com.paycore.notification.domain.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    List<NotificationLog> findByPaymentIdOrderByReceivedAtDesc(UUID paymentId);

    List<NotificationLog> findByMerchantIdOrderByReceivedAtDesc(UUID merchantId);
}