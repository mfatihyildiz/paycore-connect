package com.paycore.settlement.controller;

import com.paycore.settlement.domain.SettlementStatus;
import com.paycore.settlement.dto.MerchantSettlementSummaryResponse;
import com.paycore.settlement.dto.SettlementResponse;
import com.paycore.settlement.exception.GlobalExceptionHandler;
import com.paycore.settlement.exception.SettlementNotFoundException;
import com.paycore.settlement.service.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SettlementControllerTest {

    private MockMvc mockMvc;
    private SettlementService settlementService;

    @BeforeEach
    void setUp() {
        settlementService = mock(SettlementService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new SettlementController(settlementService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getSettlementByPaymentId_shouldReturnSettlementResponse_whenSettlementExists() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        SettlementResponse response = createSettlementResponse(
                paymentId,
                merchantId,
                new BigDecimal("1000.00"),
                new BigDecimal("25.00"),
                new BigDecimal("975.00")
        );

        when(settlementService.getSettlementByPaymentId(paymentId))
                .thenReturn(response);

        mockMvc.perform(get("/api/settlements/payments/{paymentId}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.eventId").value(response.eventId().toString()))
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.grossAmount").value(1000.00))
                .andExpect(jsonPath("$.commissionRate").value(0.025))
                .andExpect(jsonPath("$.commissionAmount").value(25.00))
                .andExpect(jsonPath("$.netAmount").value(975.00))
                .andExpect(jsonPath("$.currency").value("TRY"))
                .andExpect(jsonPath("$.status").value("CALCULATED"))
                .andExpect(jsonPath("$.orderId").value("ORDER-SETTLEMENT-CONTROLLER-1001"))
                .andExpect(jsonPath("$.sourceEventType").value("PAYMENT_AUTHORIZED"));

        verify(settlementService).getSettlementByPaymentId(paymentId);
    }

    @Test
    void getSettlementByPaymentId_shouldReturnNotFound_whenSettlementDoesNotExist() throws Exception {
        UUID paymentId = UUID.randomUUID();

        when(settlementService.getSettlementByPaymentId(paymentId))
                .thenThrow(new SettlementNotFoundException(paymentId));

        mockMvc.perform(get("/api/settlements/payments/{paymentId}", paymentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").exists());

        verify(settlementService).getSettlementByPaymentId(paymentId);
    }

    @Test
    void getSettlementsByMerchantId_shouldReturnMerchantSettlementList() throws Exception {
        UUID merchantId = UUID.randomUUID();

        SettlementResponse firstSettlement = createSettlementResponse(
                UUID.randomUUID(),
                merchantId,
                new BigDecimal("1000.00"),
                new BigDecimal("25.00"),
                new BigDecimal("975.00")
        );

        SettlementResponse secondSettlement = createSettlementResponse(
                UUID.randomUUID(),
                merchantId,
                new BigDecimal("2000.00"),
                new BigDecimal("50.00"),
                new BigDecimal("1950.00")
        );

        when(settlementService.getSettlementsByMerchantId(merchantId))
                .thenReturn(List.of(firstSettlement, secondSettlement));

        mockMvc.perform(get("/api/settlements/merchants/{merchantId}", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentId").value(firstSettlement.paymentId().toString()))
                .andExpect(jsonPath("$[0].merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$[0].grossAmount").value(1000.00))
                .andExpect(jsonPath("$[0].commissionAmount").value(25.00))
                .andExpect(jsonPath("$[0].netAmount").value(975.00))
                .andExpect(jsonPath("$[0].status").value("CALCULATED"))
                .andExpect(jsonPath("$[1].paymentId").value(secondSettlement.paymentId().toString()))
                .andExpect(jsonPath("$[1].merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$[1].grossAmount").value(2000.00))
                .andExpect(jsonPath("$[1].commissionAmount").value(50.00))
                .andExpect(jsonPath("$[1].netAmount").value(1950.00))
                .andExpect(jsonPath("$[1].status").value("CALCULATED"));

        verify(settlementService).getSettlementsByMerchantId(merchantId);
    }

    @Test
    void getMerchantSettlementSummary_shouldReturnSummaryResponse() throws Exception {
        UUID merchantId = UUID.randomUUID();

        MerchantSettlementSummaryResponse response = new MerchantSettlementSummaryResponse(
                merchantId,
                "TRY",
                2,
                new BigDecimal("3000.00"),
                new BigDecimal("75.00"),
                new BigDecimal("2925.00")
        );

        when(settlementService.getMerchantSettlementSummary(merchantId))
                .thenReturn(response);

        mockMvc.perform(get("/api/settlements/merchants/{merchantId}/summary", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.currency").value("TRY"))
                .andExpect(jsonPath("$.settlementCount").value(2))
                .andExpect(jsonPath("$.totalGrossAmount").value(3000.00))
                .andExpect(jsonPath("$.totalCommissionAmount").value(75.00))
                .andExpect(jsonPath("$.totalNetAmount").value(2925.00));

        verify(settlementService).getMerchantSettlementSummary(merchantId);
    }

    @Test
    void getMerchantSettlementSummary_shouldReturnZeroSummary_whenMerchantHasNoSettlements() throws Exception {
        UUID merchantId = UUID.randomUUID();

        MerchantSettlementSummaryResponse response = new MerchantSettlementSummaryResponse(
                merchantId,
                null,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        when(settlementService.getMerchantSettlementSummary(merchantId))
                .thenReturn(response);

        mockMvc.perform(get("/api/settlements/merchants/{merchantId}/summary", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.currency").doesNotExist())
                .andExpect(jsonPath("$.settlementCount").value(0))
                .andExpect(jsonPath("$.totalGrossAmount").value(0))
                .andExpect(jsonPath("$.totalCommissionAmount").value(0))
                .andExpect(jsonPath("$.totalNetAmount").value(0));

        verify(settlementService).getMerchantSettlementSummary(merchantId);
    }

    @Test
    void getSettlementByPaymentId_shouldReturnBadRequest_whenPaymentIdIsInvalidUuid() throws Exception {
        mockMvc.perform(get("/api/settlements/payments/{paymentId}", "invalid-uuid"))
                .andExpect(status().isBadRequest());

        verify(settlementService, never()).getSettlementByPaymentId(any(UUID.class));
    }

    @Test
    void getSettlementsByMerchantId_shouldReturnBadRequest_whenMerchantIdIsInvalidUuid() throws Exception {
        mockMvc.perform(get("/api/settlements/merchants/{merchantId}", "invalid-uuid"))
                .andExpect(status().isBadRequest());

        verify(settlementService, never()).getSettlementsByMerchantId(any(UUID.class));
    }

    private SettlementResponse createSettlementResponse(
            UUID paymentId,
            UUID merchantId,
            BigDecimal grossAmount,
            BigDecimal commissionAmount,
            BigDecimal netAmount
    ) {
        LocalDateTime now = LocalDateTime.now();

        return new SettlementResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                paymentId,
                merchantId,
                grossAmount,
                new BigDecimal("0.025"),
                commissionAmount,
                netAmount,
                "TRY",
                SettlementStatus.CALCULATED,
                LocalDate.now(),
                "ORDER-SETTLEMENT-CONTROLLER-1001",
                "PAYMENT_AUTHORIZED",
                now.minusSeconds(5),
                now,
                now
        );
    }
}