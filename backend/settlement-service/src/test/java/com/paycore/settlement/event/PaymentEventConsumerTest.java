package com.paycore.settlement.event;

import com.paycore.settlement.service.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class PaymentEventConsumerTest {

    private SettlementService settlementService;
    private PaymentEventConsumer paymentEventConsumer;

    @BeforeEach
    void setUp() {
        settlementService = mock(SettlementService.class);
        paymentEventConsumer = new PaymentEventConsumer(settlementService);
    }

    @Test
    void consumePaymentEvent_shouldDelegateEventToSettlementService() {
        PaymentEvent event = createPaymentEvent();

        paymentEventConsumer.consumePaymentEvent(event);

        verify(settlementService).processPaymentEvent(event);
        verifyNoMoreInteractions(settlementService);
    }

    @Test
    void consumePaymentEvent_shouldDelegateNullEventToSettlementService() {
        paymentEventConsumer.consumePaymentEvent(null);

        verify(settlementService).processPaymentEvent(isNull());
        verifyNoMoreInteractions(settlementService);
    }

    private PaymentEvent createPaymentEvent() {
        return new PaymentEvent(
                UUID.randomUUID(),
                "PAYMENT_AUTHORIZED",
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-SETTLEMENT-CONSUMER-1001",
                "AUTHORIZED",
                "MOCK_BANK",
                "MOCK-BANK-REF-1001",
                "00",
                "APPROVED",
                LocalDateTime.now()
        );
    }
}