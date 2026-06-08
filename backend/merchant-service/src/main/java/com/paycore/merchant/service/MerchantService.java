package com.paycore.merchant.service;

import com.paycore.merchant.domain.Merchant;
import com.paycore.merchant.domain.MerchantStatus;
import com.paycore.merchant.dto.ApiKeyValidationResponse;
import com.paycore.merchant.dto.CreateMerchantRequest;
import com.paycore.merchant.dto.GenerateApiKeyResponse;
import com.paycore.merchant.dto.MerchantResponse;
import com.paycore.merchant.dto.UpdateMerchantStatusRequest;
import com.paycore.merchant.exception.DuplicateMerchantEmailException;
import com.paycore.merchant.exception.MerchantNotFoundException;
import com.paycore.merchant.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private static final String API_KEY_CACHE_PREFIX = "merchant:api-key:";
    private static final Duration API_KEY_CACHE_TTL = Duration.ofMinutes(30);

    private final MerchantRepository merchantRepository;
    private final StringRedisTemplate stringRedisTemplate;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public MerchantResponse createMerchant(CreateMerchantRequest request) {
        if (merchantRepository.existsByEmail(request.email())) {
            throw new DuplicateMerchantEmailException(request.email());
        }

        Merchant merchant = Merchant.builder()
                .name(request.name())
                .email(request.email())
                .apiKey(generateUniqueApiKey())
                .status(MerchantStatus.ACTIVE)
                .build();

        Merchant savedMerchant = merchantRepository.save(merchant);
        cacheActiveMerchantApiKey(savedMerchant);

        return toResponse(savedMerchant);
    }

    @Transactional(readOnly = true)
    public List<MerchantResponse> getAllMerchants() {
        return merchantRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MerchantResponse getMerchantById(UUID merchantId) {
        Merchant merchant = findMerchantOrThrow(merchantId);
        return toResponse(merchant);
    }

    @Transactional
    public MerchantResponse updateMerchantStatus(UUID merchantId, UpdateMerchantStatusRequest request) {
        Merchant merchant = findMerchantOrThrow(merchantId);
        merchant.setStatus(request.status());

        Merchant updatedMerchant = merchantRepository.save(merchant);

        evictApiKeyCache(updatedMerchant.getApiKey());
        cacheActiveMerchantApiKey(updatedMerchant);

        return toResponse(updatedMerchant);
    }

    @Transactional
    public GenerateApiKeyResponse regenerateApiKey(UUID merchantId) {
        Merchant merchant = findMerchantOrThrow(merchantId);

        String oldApiKey = merchant.getApiKey();
        String newApiKey = generateUniqueApiKey();

        merchant.setApiKey(newApiKey);
        Merchant updatedMerchant = merchantRepository.save(merchant);

        evictApiKeyCache(oldApiKey);
        cacheActiveMerchantApiKey(updatedMerchant);

        return new GenerateApiKeyResponse(updatedMerchant.getId(), newApiKey);
    }

    @Transactional(readOnly = true)
    public ApiKeyValidationResponse validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return invalidApiKeyResponse();
        }

        String cacheKey = buildApiKeyCacheKey(apiKey);
        String cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);

        if (cachedValue != null) {
            return parseCachedApiKeyValidation(cachedValue);
        }

        return merchantRepository.findByApiKey(apiKey)
                .filter(merchant -> merchant.getStatus() == MerchantStatus.ACTIVE)
                .map(merchant -> {
                    cacheActiveMerchantApiKey(merchant);

                    return new ApiKeyValidationResponse(
                            true,
                            merchant.getId(),
                            merchant.getName(),
                            merchant.getStatus()
                    );
                })
                .orElseGet(this::invalidApiKeyResponse);
    }

    private Merchant findMerchantOrThrow(UUID merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));
    }

    private String generateUniqueApiKey() {
        String apiKey;

        do {
            byte[] randomBytes = new byte[32];
            secureRandom.nextBytes(randomBytes);
            apiKey = "pk_live_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        } while (merchantRepository.existsByApiKey(apiKey));

        return apiKey;
    }

    private void cacheActiveMerchantApiKey(Merchant merchant) {
        if (merchant.getStatus() != MerchantStatus.ACTIVE) {
            return;
        }

        String cacheKey = buildApiKeyCacheKey(merchant.getApiKey());

        String cacheValue = merchant.getId()
                + "|" + merchant.getName()
                + "|" + merchant.getStatus();

        stringRedisTemplate.opsForValue().set(cacheKey, cacheValue, API_KEY_CACHE_TTL);
    }

    private void evictApiKeyCache(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }

        stringRedisTemplate.delete(buildApiKeyCacheKey(apiKey));
    }

    private String buildApiKeyCacheKey(String apiKey) {
        return API_KEY_CACHE_PREFIX + apiKey;
    }

    private ApiKeyValidationResponse parseCachedApiKeyValidation(String cachedValue) {
        String[] parts = cachedValue.split("\\|");

        if (parts.length != 3) {
            return invalidApiKeyResponse();
        }

        return new ApiKeyValidationResponse(
                true,
                UUID.fromString(parts[0]),
                parts[1],
                MerchantStatus.valueOf(parts[2])
        );
    }

    private ApiKeyValidationResponse invalidApiKeyResponse() {
        return new ApiKeyValidationResponse(
                false,
                null,
                null,
                null
        );
    }

    private MerchantResponse toResponse(Merchant merchant) {
        return new MerchantResponse(
                merchant.getId(),
                merchant.getName(),
                merchant.getEmail(),
                merchant.getApiKey(),
                merchant.getStatus(),
                merchant.getCreatedAt(),
                merchant.getUpdatedAt()
        );
    }
}