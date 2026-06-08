package com.paycore.settlement.repository;

import com.paycore.settlement.domain.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    boolean existsByEventId(UUID eventId);

    boolean existsByPaymentId(UUID paymentId);

    Optional<Settlement> findByPaymentId(UUID paymentId);

    List<Settlement> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}