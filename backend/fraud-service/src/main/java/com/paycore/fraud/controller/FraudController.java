package com.paycore.fraud.controller;

import com.paycore.fraud.dto.FraudCheckRequest;
import com.paycore.fraud.dto.FraudCheckResponse;
import com.paycore.fraud.service.FraudService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/fraud")
@RequiredArgsConstructor
public class FraudController {

    private final FraudService fraudService;

    @PostMapping("/check")
    public FraudCheckResponse checkPaymentRisk(@Valid @RequestBody FraudCheckRequest request) {
        return fraudService.checkPaymentRisk(request);
    }

    @GetMapping("/payments/{paymentId}")
    public List<FraudCheckResponse> getChecksByPaymentId(@PathVariable UUID paymentId) {
        return fraudService.getChecksByPaymentId(paymentId);
    }

    @GetMapping("/merchants/{merchantId}")
    public List<FraudCheckResponse> getChecksByMerchantId(@PathVariable UUID merchantId) {
        return fraudService.getChecksByMerchantId(merchantId);
    }
}