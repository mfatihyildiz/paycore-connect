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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
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

        Merchant savedMerchant;

        try {
            savedMerchant = merchantRepository.saveAndFlush(merchant);
        } catch (DataIntegrityViolationException ex) {
            if (isEmailUniqueViolation(ex)) {
                throw new DuplicateMerchantEmailException(request.email());
            }
            throw ex;
        }

        runAfterCommit(() -> cacheActiveMerchantApiKey(savedMerchant));

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

        runAfterCommit(() -> {evictApiKeyCache(updatedMerchant.getApiKey());cacheActiveMerchantApiKey(updatedMerchant);});

        return toResponse(updatedMerchant);
    }

    @Transactional
    public GenerateApiKeyResponse regenerateApiKey(UUID merchantId) {
        Merchant merchant = findMerchantOrThrow(merchantId);

        String oldApiKey = merchant.getApiKey();
        String newApiKey = generateUniqueApiKey();

        merchant.setApiKey(newApiKey);
        Merchant updatedMerchant = merchantRepository.save(merchant);

        runAfterCommit(() -> {evictApiKeyCache(oldApiKey);cacheActiveMerchantApiKey(updatedMerchant);});

        return new GenerateApiKeyResponse(updatedMerchant.getId(), newApiKey);
    }

    @Transactional(readOnly = true)
    public ApiKeyValidationResponse validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return invalidApiKeyResponse();
        }

        String cacheKey = buildApiKeyCacheKey(apiKey);
        String cachedValue = getCachedValue(cacheKey);

        if (cachedValue != null) {
            Optional<ApiKeyValidationResponse> cachedResponse = parseCachedApiKeyValidation(cachedValue);

            if (cachedResponse.isEmpty()) {
                evictApiKeyCache(apiKey);
            }
        }

        Optional<Merchant> merchantOptional = merchantRepository.findByApiKey(apiKey);

        if (merchantOptional.isEmpty()) {
            evictApiKeyCache(apiKey);
            return invalidApiKeyResponse();
        }

        Merchant merchant = merchantOptional.get();

        if (merchant.getStatus() != MerchantStatus.ACTIVE) {
            evictApiKeyCache(apiKey);
            return invalidApiKeyResponse();
        }

        cacheActiveMerchantApiKey(merchant);

        return new ApiKeyValidationResponse(true, merchant.getId(), merchant.getName(), merchant.getStatus());
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

    private String getCachedValue(String cacheKey) {
        try {
            return stringRedisTemplate.opsForValue().get(cacheKey);
        } catch (DataAccessException ex) {
            log.warn("Merchant API key cache read failed. Falling back to PostgreSQL.");
            return null;
        }
    }

    private void cacheActiveMerchantApiKey(Merchant merchant) {
        if (merchant.getStatus() != MerchantStatus.ACTIVE) {
            return;
        }

        String cacheKey = buildApiKeyCacheKey(merchant.getApiKey());

        String cacheValue = merchant.getId()
                + "|" + merchant.getName()
                + "|" + merchant.getStatus();

        try {
            stringRedisTemplate.opsForValue().set(cacheKey, cacheValue, API_KEY_CACHE_TTL);
        } catch (DataAccessException ex) {
            log.warn("Merchant API key cache write failed. PostgreSQL remains authoritative.");
        }
    }

    private void evictApiKeyCache(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }

        try {
            stringRedisTemplate.delete(buildApiKeyCacheKey(apiKey));
        } catch (DataAccessException ex) {
            log.warn("Merchant API key cache eviction failed. PostgreSQL remains authoritative.");
        }
    }

    private String buildApiKeyCacheKey(String apiKey) {
        return API_KEY_CACHE_PREFIX + apiKey;
    }

    private Optional<ApiKeyValidationResponse> parseCachedApiKeyValidation(
            String cachedValue
    ) {
        try {
            String[] parts = cachedValue.split("\\|", -1);

            if (parts.length != 3) {
                return Optional.empty();
            }

            UUID merchantId = UUID.fromString(parts[0]);
            String merchantName = parts[1];
            MerchantStatus status = MerchantStatus.valueOf(parts[2]);

            if (merchantName.isBlank() || status != MerchantStatus.ACTIVE) {
                return Optional.empty();
            }

            return Optional.of(new ApiKeyValidationResponse(true, merchantId, merchantName, status));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
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

    private boolean isEmailUniqueViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;

        while (cause != null) {
            String message = cause.getMessage();

            if (message != null && message.contains("uk_merchants_email")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive() && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    }
            );
            return;
        }
        action.run();
    }
}