package com.paycore.payment.repository;

import com.paycore.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    boolean existsByMerchantIdAndOrderId(UUID merchantId, String orderId);
}