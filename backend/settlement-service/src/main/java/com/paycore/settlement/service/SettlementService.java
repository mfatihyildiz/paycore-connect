package com.paycore.settlement.service;

import com.paycore.settlement.domain.Settlement;
import com.paycore.settlement.domain.SettlementStatus;
import com.paycore.settlement.dto.MerchantSettlementSummaryResponse;
import com.paycore.settlement.dto.SettlementResponse;
import com.paycore.settlement.event.PaymentEvent;
import com.paycore.settlement.exception.SettlementNotFoundException;
import com.paycore.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private static final String AUTHORIZED_EVENT_TYPE = "PAYMENT_AUTHORIZED";

    private final SettlementRepository settlementRepository;

    @Value("${paycore.settlement.commission-rate}")
    private BigDecimal commissionRate;

    @Transactional
    public void processPaymentEvent(PaymentEvent event) {
        if (!AUTHORIZED_EVENT_TYPE.equals(event.eventType())) {
            return;
        }

        if (settlementRepository.existsByEventId(event.eventId())
                || settlementRepository.existsByPaymentId(event.paymentId())) {
            return;
        }

        BigDecimal grossAmount = event.amount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal commissionAmount = grossAmount
                .multiply(commissionRate)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal netAmount = grossAmount
                .subtract(commissionAmount)
                .setScale(2, RoundingMode.HALF_UP);

        Settlement settlement = Settlement.builder()
                .eventId(event.eventId())
                .paymentId(event.paymentId())
                .merchantId(event.merchantId())
                .grossAmount(grossAmount)
                .commissionRate(commissionRate)
                .commissionAmount(commissionAmount)
                .netAmount(netAmount)
                .currency(event.currency())
                .status(SettlementStatus.CALCULATED)
                .settlementDate(LocalDate.now())
                .orderId(event.orderId())
                .sourceEventType(event.eventType())
                .sourceEventOccurredAt(event.occurredAt())
                .build();

        settlementRepository.save(settlement);
    }

    @Transactional(readOnly = true)
    public SettlementResponse getSettlementByPaymentId(UUID paymentId) {
        Settlement settlement = settlementRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new SettlementNotFoundException(paymentId));

        return toResponse(settlement);
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> getSettlementsByMerchantId(UUID merchantId) {
        return settlementRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MerchantSettlementSummaryResponse getMerchantSettlementSummary(UUID merchantId) {
        List<Settlement> settlements = settlementRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId);

        if (settlements.isEmpty()) {
            return new MerchantSettlementSummaryResponse(
                    merchantId,
                    null,
                    0,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            );
        }

        String currency = settlements.get(0).getCurrency();

        BigDecimal totalGrossAmount = settlements.stream()
                .map(Settlement::getGrossAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalCommissionAmount = settlements.stream()
                .map(Settlement::getCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalNetAmount = settlements.stream()
                .map(Settlement::getNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return new MerchantSettlementSummaryResponse(
                merchantId,
                currency,
                settlements.size(),
                totalGrossAmount,
                totalCommissionAmount,
                totalNetAmount
        );
    }

    private SettlementResponse toResponse(Settlement settlement) {
        return new SettlementResponse(
                settlement.getId(),
                settlement.getEventId(),
                settlement.getPaymentId(),
                settlement.getMerchantId(),
                settlement.getGrossAmount(),
                settlement.getCommissionRate(),
                settlement.getCommissionAmount(),
                settlement.getNetAmount(),
                settlement.getCurrency(),
                settlement.getStatus(),
                settlement.getSettlementDate(),
                settlement.getOrderId(),
                settlement.getSourceEventType(),
                settlement.getSourceEventOccurredAt(),
                settlement.getCreatedAt(),
                settlement.getUpdatedAt()
        );
    }
}