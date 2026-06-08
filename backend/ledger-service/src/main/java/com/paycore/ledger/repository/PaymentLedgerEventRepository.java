package com.paycore.ledger.repository;

import com.paycore.ledger.domain.PaymentLedgerEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentLedgerEventRepository extends JpaRepository<PaymentLedgerEvent, UUID> {

    boolean existsByEventId(UUID eventId);

    List<PaymentLedgerEvent> findByPaymentIdOrderByOccurredAtAsc(UUID paymentId);

    List<PaymentLedgerEvent> findByMerchantIdOrderByOccurredAtDesc(UUID merchantId);
}