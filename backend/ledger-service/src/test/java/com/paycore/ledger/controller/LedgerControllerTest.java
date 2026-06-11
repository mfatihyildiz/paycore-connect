package com.paycore.ledger.controller;

import com.paycore.ledger.dto.PaymentLedgerEventResponse;
import com.paycore.ledger.dto.PaymentLedgerStateResponse;
import com.paycore.ledger.exception.GlobalExceptionHandler;
import com.paycore.ledger.exception.PaymentLedgerNotFoundException;
import com.paycore.ledger.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LedgerControllerTest {

    private MockMvc mockMvc;
    private LedgerService ledgerService;

    @BeforeEach
    void setUp() {
        ledgerService = mock(LedgerService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new LedgerController(ledgerService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getEventsByPaymentId_shouldReturnPaymentLedgerEvents() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        PaymentLedgerEventResponse firstEvent = createLedgerEventResponse(
                paymentId,
                merchantId,
                "PAYMENT_INITIATED",
                "INITIATED",
                LocalDateTime.now().minusMinutes(2)
        );

        PaymentLedgerEventResponse secondEvent = createLedgerEventResponse(
                paymentId,
                merchantId,
                "PAYMENT_AUTHORIZED",
                "AUTHORIZED",
                LocalDateTime.now()
        );

        when(ledgerService.getEventsByPaymentId(paymentId))
                .thenReturn(List.of(firstEvent, secondEvent));

        mockMvc.perform(get("/api/ledger/payments/{paymentId}/events", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(firstEvent.id().toString()))
                .andExpect(jsonPath("$[0].eventId").value(firstEvent.eventId().toString()))
                .andExpect(jsonPath("$[0].eventType").value("PAYMENT_INITIATED"))
                .andExpect(jsonPath("$[0].paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$[0].merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$[0].amount").value(1000.00))
                .andExpect(jsonPath("$[0].currency").value("TRY"))
                .andExpect(jsonPath("$[0].orderId").value("ORDER-LEDGER-CONTROLLER-1001"))
                .andExpect(jsonPath("$[0].paymentStatus").value("INITIATED"))
                .andExpect(jsonPath("$[0].providerType").value("MOCK_BANK"))
                .andExpect(jsonPath("$[1].eventType").value("PAYMENT_AUTHORIZED"))
                .andExpect(jsonPath("$[1].paymentStatus").value("AUTHORIZED"));

        verify(ledgerService).getEventsByPaymentId(paymentId);
    }

    @Test
    void getEventsByMerchantId_shouldReturnMerchantLedgerEvents() throws Exception {
        UUID merchantId = UUID.randomUUID();

        PaymentLedgerEventResponse firstEvent = createLedgerEventResponse(
                UUID.randomUUID(),
                merchantId,
                "PAYMENT_AUTHORIZED",
                "AUTHORIZED",
                LocalDateTime.now()
        );

        PaymentLedgerEventResponse secondEvent = createLedgerEventResponse(
                UUID.randomUUID(),
                merchantId,
                "PAYMENT_FAILED",
                "FAILED",
                LocalDateTime.now().minusMinutes(1)
        );

        when(ledgerService.getEventsByMerchantId(merchantId))
                .thenReturn(List.of(firstEvent, secondEvent));

        mockMvc.perform(get("/api/ledger/merchants/{merchantId}/events", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$[0].eventType").value("PAYMENT_AUTHORIZED"))
                .andExpect(jsonPath("$[0].paymentStatus").value("AUTHORIZED"))
                .andExpect(jsonPath("$[1].merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$[1].eventType").value("PAYMENT_FAILED"))
                .andExpect(jsonPath("$[1].paymentStatus").value("FAILED"));

        verify(ledgerService).getEventsByMerchantId(merchantId);
    }

    @Test
    void reconstructPaymentState_shouldReturnPaymentLedgerState() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        LocalDateTime firstOccurredAt = LocalDateTime.now().minusMinutes(5);
        LocalDateTime lastOccurredAt = LocalDateTime.now();

        PaymentLedgerStateResponse response = new PaymentLedgerStateResponse(
                paymentId,
                merchantId,
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-LEDGER-CONTROLLER-1001",
                "AUTHORIZED",
                "MOCK_BANK",
                "MOCK-BANK-REF-1001",
                "00",
                "APPROVED",
                2,
                "PAYMENT_INITIATED",
                "PAYMENT_AUTHORIZED",
                firstOccurredAt,
                lastOccurredAt
        );

        when(ledgerService.reconstructPaymentState(paymentId))
                .thenReturn(response);

        mockMvc.perform(get("/api/ledger/payments/{paymentId}/state", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.amount").value(1000.00))
                .andExpect(jsonPath("$.currency").value("TRY"))
                .andExpect(jsonPath("$.orderId").value("ORDER-LEDGER-CONTROLLER-1001"))
                .andExpect(jsonPath("$.currentStatus").value("AUTHORIZED"))
                .andExpect(jsonPath("$.providerType").value("MOCK_BANK"))
                .andExpect(jsonPath("$.providerReferenceId").value("MOCK-BANK-REF-1001"))
                .andExpect(jsonPath("$.providerResponseCode").value("00"))
                .andExpect(jsonPath("$.providerResponseMessage").value("APPROVED"))
                .andExpect(jsonPath("$.eventCount").value(2))
                .andExpect(jsonPath("$.firstEventType").value("PAYMENT_INITIATED"))
                .andExpect(jsonPath("$.lastEventType").value("PAYMENT_AUTHORIZED"));

        verify(ledgerService).reconstructPaymentState(paymentId);
    }

    @Test
    void reconstructPaymentState_shouldReturnNotFound_whenLedgerEventsDoNotExist() throws Exception {
        UUID paymentId = UUID.randomUUID();

        when(ledgerService.reconstructPaymentState(paymentId))
                .thenThrow(new PaymentLedgerNotFoundException(paymentId));

        mockMvc.perform(get("/api/ledger/payments/{paymentId}/state", paymentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("No ledger events found for payment id: " + paymentId));

        verify(ledgerService).reconstructPaymentState(paymentId);
    }

    @Test
    void getEventsByPaymentId_shouldReturnBadRequest_whenPaymentIdIsInvalidUuid() throws Exception {
        mockMvc.perform(get("/api/ledger/payments/{paymentId}/events", "invalid-uuid"))
                .andExpect(status().isBadRequest());

        verify(ledgerService, never()).getEventsByPaymentId(any(UUID.class));
    }

    private PaymentLedgerEventResponse createLedgerEventResponse(
            UUID paymentId,
            UUID merchantId,
            String eventType,
            String paymentStatus,
            LocalDateTime occurredAt
    ) {
        return new PaymentLedgerEventResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                eventType,
                paymentId,
                merchantId,
                new BigDecimal("1000.00"),
                "TRY",
                "ORDER-LEDGER-CONTROLLER-1001",
                paymentStatus,
                "MOCK_BANK",
                "MOCK-BANK-REF-1001",
                "00",
                "APPROVED",
                occurredAt,
                LocalDateTime.now()
        );
    }
}