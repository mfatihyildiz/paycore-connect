package com.paycore.merchant.controller;

import com.paycore.merchant.dto.ApiKeyValidationResponse;
import com.paycore.merchant.dto.CreateMerchantRequest;
import com.paycore.merchant.dto.GenerateApiKeyResponse;
import com.paycore.merchant.dto.MerchantResponse;
import com.paycore.merchant.dto.UpdateMerchantStatusRequest;
import com.paycore.merchant.service.MerchantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MerchantResponse createMerchant(@Valid @RequestBody CreateMerchantRequest request) {
        return merchantService.createMerchant(request);
    }

    @GetMapping
    public List<MerchantResponse> getAllMerchants() {
        return merchantService.getAllMerchants();
    }

    @GetMapping("/{merchantId}")
    public MerchantResponse getMerchantById(@PathVariable UUID merchantId) {
        return merchantService.getMerchantById(merchantId);
    }

    @PatchMapping("/{merchantId}/status")
    public MerchantResponse updateMerchantStatus(
            @PathVariable UUID merchantId,
            @Valid @RequestBody UpdateMerchantStatusRequest request
    ) {
        return merchantService.updateMerchantStatus(merchantId, request);
    }

    @PostMapping("/{merchantId}/api-key")
    public GenerateApiKeyResponse regenerateApiKey(@PathVariable UUID merchantId) {
        return merchantService.regenerateApiKey(merchantId);
    }

    @GetMapping("/validate-api-key")
    public ApiKeyValidationResponse validateApiKey(@RequestHeader("X-API-Key") String apiKey) {
        return merchantService.validateApiKey(apiKey);
    }
}