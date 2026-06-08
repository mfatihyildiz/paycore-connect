package com.paycore.settlement.controller;

import com.paycore.settlement.dto.MerchantSettlementSummaryResponse;
import com.paycore.settlement.dto.SettlementResponse;
import com.paycore.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @GetMapping("/payments/{paymentId}")
    public SettlementResponse getSettlementByPaymentId(@PathVariable UUID paymentId) {
        return settlementService.getSettlementByPaymentId(paymentId);
    }

    @GetMapping("/merchants/{merchantId}")
    public List<SettlementResponse> getSettlementsByMerchantId(@PathVariable UUID merchantId) {
        return settlementService.getSettlementsByMerchantId(merchantId);
    }

    @GetMapping("/merchants/{merchantId}/summary")
    public MerchantSettlementSummaryResponse getMerchantSettlementSummary(@PathVariable UUID merchantId) {
        return settlementService.getMerchantSettlementSummary(merchantId);
    }
}