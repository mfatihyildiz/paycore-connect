package com.paycore.payment.controller;

import com.paycore.payment.dto.PaymentInitiateRequest;
import com.paycore.payment.dto.PaymentResponse;
import com.paycore.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse initiatePayment(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            @Valid @RequestBody PaymentInitiateRequest request
    ) {
        return paymentService.initiatePayment(apiKey, extractClientIp(forwardedFor), request);
    }

    private String extractClientIp(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return "unknown";
        }

        return forwardedFor.split(",")[0].trim();
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse getPaymentById(@PathVariable UUID paymentId) {
        return paymentService.getPaymentById(paymentId);
    }

    @GetMapping("/merchant/{merchantId}")
    public List<PaymentResponse> getPaymentsByMerchantId(@PathVariable UUID merchantId) {
        return paymentService.getPaymentsByMerchantId(merchantId);
    }
}