package com.paycore.ledger.controller;

import com.paycore.ledger.dto.PaymentLedgerEventResponse;
import com.paycore.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.paycore.ledger.dto.PaymentLedgerStateResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    @GetMapping("/payments/{paymentId}/events")
    public List<PaymentLedgerEventResponse> getEventsByPaymentId(@PathVariable UUID paymentId) {
        return ledgerService.getEventsByPaymentId(paymentId);
    }

    @GetMapping("/merchants/{merchantId}/events")
    public List<PaymentLedgerEventResponse> getEventsByMerchantId(@PathVariable UUID merchantId) {
        return ledgerService.getEventsByMerchantId(merchantId);
    }

    @GetMapping("/payments/{paymentId}/state")
    public PaymentLedgerStateResponse reconstructPaymentState(@PathVariable UUID paymentId) {
        return ledgerService.reconstructPaymentState(paymentId);
    }
}
