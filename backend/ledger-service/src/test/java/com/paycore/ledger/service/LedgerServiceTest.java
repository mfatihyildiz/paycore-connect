package com.paycore.ledger.service;

import com.paycore.ledger.domain.PaymentLedgerEvent;
import com.paycore.ledger.dto.PaymentLedgerEventResponse;
import com.paycore.ledger.dto.PaymentLedgerStateResponse;
import com.paycore.ledger.event.PaymentEvent;
import com.paycore.ledger.exception.PaymentLedgerNotFoundException;
import com.paycore.ledger.repository.PaymentLedgerEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private PaymentLedgerEventRepository paymentLedgerEventRepository;

    private LedgerService ledgerService;

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(paymentLedgerEventRepository);
    }

    @Test
    void savePaymentEvent_shouldSaveLedgerEvent_whenEventIsNotAlreadyConsumed() {
        PaymentEvent event = createPaymentEvent(
                "PAYMENT_INITIATED",
                "INITIATED",
                LocalDateTime.now()
        );

        when(paymentLedgerEventRepository.existsByEventId(event.eventId()))
                .thenReturn(false);

        when(paymentLedgerEventRepository.save(any(PaymentLedgerEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ledgerService.savePaymentEvent(event);

        ArgumentCaptor<PaymentLedgerEvent> captor =
                ArgumentCaptor.forClass(PaymentLedgerEvent.class);

        verify(paymentLedgerEventRepository).save(captor.capture());

        PaymentLedgerEvent savedEvent = captor.getValue();

        assertThat(savedEvent.getEventId()).isEqualTo(event.eventId());
        assertThat(savedEvent.getEventType()).isEqualTo(event.eventType());
        assertThat(savedEvent.getPaymentId()).isEqualTo(event.paymentId());
        assertThat(savedEvent.getMerchantId()).isEqualTo(event.merchantId());
        assertThat(savedEvent.getAmount()).isEqualByComparingTo(event.amount());
        assertThat(savedEvent.getCurrency()).isEqualTo(event.currency());
        assertThat(savedEvent.getOrderId()).isEqualTo(event.orderId());
        assertThat(savedEvent.getPaymentStatus()).isEqualTo(event.paymentStatus());
        assertThat(savedEvent.getProviderType()).isEqualTo(event.providerType());
        assertThat(savedEvent.getProviderReferenceId()).isEqualTo(event.providerReferenceId());
        assertThat(savedEvent.getProviderResponseCode()).isEqualTo(event.providerResponseCode());
        assertThat(savedEvent.getProviderResponseMessage()).isEqualTo(event.providerResponseMessage());
        assertThat(savedEvent.getOccurredAt()).isEqualTo(event.occurredAt());
        assertThat(savedEvent.getConsumedAt()).isNotNull();
    }

    @Test
    void savePaymentEvent_shouldIgnoreEvent_whenEventAlreadyExists() {
        PaymentEvent event = createPaymentEvent(
                "PAYMENT_AUTHORIZED",
                "AUTHORIZED",
                LocalDateTime.now()
        );

        when(paymentLedgerEventRepository.existsByEventId(event.eventId()))
                .thenReturn(true);

        ledgerService.savePaymentEvent(event);

        verify(paymentLedgerEventRepository).existsByEventId(event.eventId());
        verify(paymentLedgerEventRepository, never()).save(any(PaymentLedgerEvent.class));
    }

    @Test
    void getEventsByPaymentId_shouldReturnMappedLedgerEventResponses() {
        UUID paymentId = UUID.randomUUID();

        PaymentLedgerEvent firstEvent = createLedgerEvent(
                paymentId,
                UUID.randomUUID(),
                "PAYMENT_INITIATED",
                "INITIATED",
                LocalDateTime.now().minusMinutes(2)
        );

        PaymentLedgerEvent secondEvent = createLedgerEvent(
                paymentId,
                firstEvent.getMerchantId(),
                "PAYMENT_AUTHORIZED",
                "AUTHORIZED",
                LocalDateTime.now()
        );

        when(paymentLedgerEventRepository.findByPaymentIdOrderByOccurredAtAsc(paymentId))
                .thenReturn(List.of(firstEvent, secondEvent));

        List<PaymentLedgerEventResponse> responses =
                ledgerService.getEventsByPaymentId(paymentId);

        assertThat(responses).hasSize(2);

        assertThat(responses.get(0).paymentId()).isEqualTo(paymentId);
        assertThat(responses.get(0).eventType()).isEqualTo("PAYMENT_INITIATED");
        assertThat(responses.get(0).paymentStatus()).isEqualTo("INITIATED");

        assertThat(responses.get(1).paymentId()).isEqualTo(paymentId);
        assertThat(responses.get(1).eventType()).isEqualTo("PAYMENT_AUTHORIZED");
        assertThat(responses.get(1).paymentStatus()).isEqualTo("AUTHORIZED");
    }

    @Test
    void getEventsByMerchantId_shouldReturnMappedLedgerEventResponses() {
        UUID merchantId = UUID.randomUUID();

        PaymentLedgerEvent firstEvent = createLedgerEvent(
                UUID.randomUUID(),
                merchantId,
                "PAYMENT_AUTHORIZED",
                "AUTHORIZED",
                LocalDateTime.now()
        );

        PaymentLedgerEvent secondEvent = createLedgerEvent(
                UUID.randomUUID(),
                merchantId,
                "PAYMENT_FAILED",
                "FAILED",
                LocalDateTime.now().minusMinutes(1)
        );

        when(paymentLedgerEventRepository.findByMerchantIdOrderByOccurredAtDesc(merchantId))
                .thenReturn(List.of(firstEvent, secondEvent));

        List<PaymentLedgerEventResponse> responses =
                ledgerService.getEventsByMerchantId(merchantId);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).merchantId()).isEqualTo(merchantId);
        assertThat(responses.get(1).merchantId()).isEqualTo(merchantId);
        assertThat(responses.get(0).eventType()).isEqualTo("PAYMENT_AUTHORIZED");
        assertThat(responses.get(1).eventType()).isEqualTo("PAYMENT_FAILED");
    }

    @Test
    void reconstructPaymentState_shouldReturnCurrentStateFromLastEvent() {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        LocalDateTime firstOccurredAt = LocalDateTime.now().minusMinutes(5);
        LocalDateTime lastOccurredAt = LocalDateTime.now();

        PaymentLedgerEvent initiatedEvent = createLedgerEvent(
                paymentId,
                merchantId,
                "PAYMENT_INITIATED",
                "INITIATED",
                firstOccurredAt
        );

        PaymentLedgerEvent authorizedEvent = createLedgerEvent(
                paymentId,
                merchantId,
                "PAYMENT_AUTHORIZED",
                "AUTHORIZED",
                lastOccurredAt
        );

        when(paymentLedgerEventRepository.findByPaymentIdOrderByOccurredAtAsc(paymentId))
                .thenReturn(List.of(initiatedEvent, authorizedEvent));

        PaymentLedgerStateResponse response =
                ledgerService.reconstructPaymentState(paymentId);

        assertThat(response).isNotNull();
        assertThat(response.paymentId()).isEqualTo(paymentId);
        assertThat(response.merchantId()).isEqualTo(merchantId);
        assertThat(response.amount()).isEqualByComparingTo(authorizedEvent.getAmount());
        assertThat(response.currency()).isEqualTo("TRY");
        assertThat(response.orderId()).isEqualTo(authorizedEvent.getOrderId());
        assertThat(response.currentStatus()).isEqualTo("AUTHORIZED");
        assertThat(response.providerType()).isEqualTo("MOCK_BANK");
        assertThat(response.providerReferenceId()).isEqualTo(authorizedEvent.getProviderReferenceId());
        assertThat(response.providerResponseCode()).isEqualTo("00");
        assertThat(response.providerResponseMessage()).isEqualTo("APPROVED");
        assertThat(response.eventCount()).isEqualTo(2);
        assertThat(response.firstEventType()).isEqualTo("PAYMENT_INITIATED");
        assertThat(response.lastEventType()).isEqualTo("PAYMENT_AUTHORIZED");
        assertThat(response.firstOccurredAt()).isEqualTo(firstOccurredAt);
        assertThat(response.lastOccurredAt()).isEqualTo(lastOccurredAt);
    }

    @Test
    void reconstructPaymentState_shouldReturnFailedState_whenLastEventIsPaymentFailed() {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        PaymentLedgerEvent initiatedEvent = createLedgerEvent(
                paymentId,
                merchantId,
                "PAYMENT_INITIATED",
                "INITIATED",
                LocalDateTime.now().minusMinutes(3)
        );

        PaymentLedgerEvent failedEvent = createLedgerEvent(
                paymentId,
                merchantId,
                "PAYMENT_FAILED",
                "FAILED",
                LocalDateTime.now()
        );

        failedEvent.setProviderResponseCode("FRAUD_REJECTED");
        failedEvent.setProviderResponseMessage("Payment rejected due to high fraud risk");

        when(paymentLedgerEventRepository.findByPaymentIdOrderByOccurredAtAsc(paymentId))
                .thenReturn(List.of(initiatedEvent, failedEvent));

        PaymentLedgerStateResponse response =
                ledgerService.reconstructPaymentState(paymentId);

        assertThat(response).isNotNull();
        assertThat(response.paymentId()).isEqualTo(paymentId);
        assertThat(response.currentStatus()).isEqualTo("FAILED");
        assertThat(response.lastEventType()).isEqualTo("PAYMENT_FAILED");
        assertThat(response.providerResponseCode()).isEqualTo("FRAUD_REJECTED");
        assertThat(response.providerResponseMessage()).isEqualTo("Payment rejected due to high fraud risk");
        assertThat(response.eventCount()).isEqualTo(2);
    }

    @Test
    void reconstructPaymentState_shouldThrowException_whenNoLedgerEventsExist() {
        UUID paymentId = UUID.randomUUID();

        when(paymentLedgerEventRepository.findByPaymentIdOrderByOccurredAtAsc(paymentId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> ledgerService.reconstructPaymentState(paymentId))
                .isInstanceOf(PaymentLedgerNotFoundException.class)
                .hasMessageContaining(paymentId.toString());
    }

    private PaymentEvent createPaymentEvent(
            String eventType,
            String paymentStatus,
            LocalDateTime occurredAt
    ) {
        return new PaymentEvent(
                UUID.randomUUID(),
                eventType,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-LEDGER-1001",
                paymentStatus,
                "MOCK_BANK",
                "PROVIDER-REF-1001",
                "00",
                "APPROVED",
                occurredAt
        );
    }

    private PaymentLedgerEvent createLedgerEvent(
            UUID paymentId,
            UUID merchantId,
            String eventType,
            String paymentStatus,
            LocalDateTime occurredAt
    ) {
        return PaymentLedgerEvent.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .paymentId(paymentId)
                .merchantId(merchantId)
                .amount(new BigDecimal("1000.00"))
                .currency("TRY")
                .orderId("ORDER-LEDGER-1001")
                .paymentStatus(paymentStatus)
                .providerType("MOCK_BANK")
                .providerReferenceId("PROVIDER-REF-1001")
                .providerResponseCode("00")
                .providerResponseMessage("APPROVED")
                .occurredAt(occurredAt)
                .consumedAt(LocalDateTime.now())
                .build();
    }
}