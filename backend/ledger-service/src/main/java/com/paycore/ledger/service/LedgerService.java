package com.paycore.ledger.service;

import com.paycore.ledger.domain.PaymentLedgerEvent;
import com.paycore.ledger.dto.PaymentLedgerEventResponse;
import com.paycore.ledger.event.PaymentEvent;
import com.paycore.ledger.repository.PaymentLedgerEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.paycore.ledger.dto.PaymentLedgerStateResponse;
import com.paycore.ledger.exception.PaymentLedgerNotFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final PaymentLedgerEventRepository paymentLedgerEventRepository;

    @Transactional
    public void savePaymentEvent(PaymentEvent event) {
        if (paymentLedgerEventRepository.existsByEventId(event.eventId())) {
            return;
        }

        PaymentLedgerEvent ledgerEvent = PaymentLedgerEvent.builder()
                .eventId(event.eventId())
                .eventType(event.eventType())
                .paymentId(event.paymentId())
                .merchantId(event.merchantId())
                .amount(event.amount())
                .currency(event.currency())
                .orderId(event.orderId())
                .paymentStatus(event.paymentStatus())
                .providerType(event.providerType())
                .providerReferenceId(event.providerReferenceId())
                .providerResponseCode(event.providerResponseCode())
                .providerResponseMessage(event.providerResponseMessage())
                .occurredAt(event.occurredAt())
                .consumedAt(LocalDateTime.now())
                .build();

        paymentLedgerEventRepository.save(ledgerEvent);
    }

    @Transactional(readOnly = true)
    public List<PaymentLedgerEventResponse> getEventsByPaymentId(UUID paymentId) {
        return paymentLedgerEventRepository.findByPaymentIdOrderByOccurredAtAsc(paymentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentLedgerEventResponse> getEventsByMerchantId(UUID merchantId) {
        return paymentLedgerEventRepository.findByMerchantIdOrderByOccurredAtDesc(merchantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private PaymentLedgerEventResponse toResponse(PaymentLedgerEvent event) {
        return new PaymentLedgerEventResponse(
                event.getId(),
                event.getEventId(),
                event.getEventType(),
                event.getPaymentId(),
                event.getMerchantId(),
                event.getAmount(),
                event.getCurrency(),
                event.getOrderId(),
                event.getPaymentStatus(),
                event.getProviderType(),
                event.getProviderReferenceId(),
                event.getProviderResponseCode(),
                event.getProviderResponseMessage(),
                event.getOccurredAt(),
                event.getConsumedAt()
        );
    }

    @Transactional(readOnly = true)
    public PaymentLedgerStateResponse reconstructPaymentState(UUID paymentId) {
        List<PaymentLedgerEvent> events = paymentLedgerEventRepository.findByPaymentIdOrderByOccurredAtAsc(paymentId);

        if (events.isEmpty()) {
            throw new PaymentLedgerNotFoundException(paymentId);
        }

        PaymentLedgerEvent firstEvent = events.get(0);
        PaymentLedgerEvent lastEvent = events.get(events.size() - 1);

        return new PaymentLedgerStateResponse(
                paymentId,
                lastEvent.getMerchantId(),
                lastEvent.getAmount(),
                lastEvent.getCurrency(),
                lastEvent.getOrderId(),
                lastEvent.getPaymentStatus(),
                lastEvent.getProviderType(),
                lastEvent.getProviderReferenceId(),
                lastEvent.getProviderResponseCode(),
                lastEvent.getProviderResponseMessage(),
                events.size(),
                firstEvent.getEventType(),
                lastEvent.getEventType(),
                firstEvent.getOccurredAt(),
                lastEvent.getOccurredAt()
        );
    }
}