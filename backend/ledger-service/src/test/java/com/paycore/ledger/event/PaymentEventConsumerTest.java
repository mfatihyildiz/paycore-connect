package com.paycore.ledger.event;

import com.paycore.ledger.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class PaymentEventConsumerTest {

    private LedgerService ledgerService;
    private PaymentEventConsumer paymentEventConsumer;

    @BeforeEach
    void setUp() {
        ledgerService = mock(LedgerService.class);
        paymentEventConsumer = new PaymentEventConsumer(ledgerService);
    }

    @Test
    void consumePaymentEvent_shouldDelegateEventToLedgerService() {
        PaymentEvent event = createPaymentEvent();

        paymentEventConsumer.consumePaymentEvent(event);

        verify(ledgerService).savePaymentEvent(event);
        verifyNoMoreInteractions(ledgerService);
    }

    @Test
    void consumePaymentEvent_shouldDelegateNullEventToLedgerService() {
        paymentEventConsumer.consumePaymentEvent(null);

        verify(ledgerService).savePaymentEvent(isNull());
        verifyNoMoreInteractions(ledgerService);
    }

    private PaymentEvent createPaymentEvent() {
        return new PaymentEvent(
                UUID.randomUUID(),
                "PAYMENT_AUTHORIZED",
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-LEDGER-CONSUMER-1001",
                "AUTHORIZED",
                "MOCK_BANK",
                "MOCK-BANK-REF-1001",
                "00",
                "APPROVED",
                LocalDateTime.now()
        );
    }
}