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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private MerchantService merchantService;

    @BeforeEach
    void setUp() {
        merchantService = new MerchantService(merchantRepository, stringRedisTemplate);

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void createMerchant_shouldCreateActiveMerchantWithGeneratedApiKeyAndCacheIt() {
        CreateMerchantRequest request = new CreateMerchantRequest(
                "Test Merchant",
                "test-merchant@example.com"
        );

        when(merchantRepository.existsByEmail(request.email())).thenReturn(false);
        when(merchantRepository.existsByApiKey(anyString())).thenReturn(false);
        when(merchantRepository.save(any(Merchant.class)))
                .thenAnswer(invocation -> {
                    Merchant merchant = invocation.getArgument(0);
                    merchant.setId(UUID.randomUUID());

                    LocalDateTime now = LocalDateTime.now();
                    merchant.setCreatedAt(now);
                    merchant.setUpdatedAt(now);

                    return merchant;
                });

        MerchantResponse response = merchantService.createMerchant(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("Test Merchant");
        assertThat(response.email()).isEqualTo("test-merchant@example.com");
        assertThat(response.apiKey()).startsWith("pk_live_");
        assertThat(response.status()).isEqualTo(MerchantStatus.ACTIVE);
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();

        verify(merchantRepository).existsByEmail(request.email());
        verify(merchantRepository).existsByApiKey(anyString());
        verify(merchantRepository).save(any(Merchant.class));

        verify(valueOperations).set(
                eq("merchant:api-key:" + response.apiKey()),
                contains(response.id().toString()),
                eq(Duration.ofMinutes(30))
        );

        verify(valueOperations).set(
                eq("merchant:api-key:" + response.apiKey()),
                contains("Test Merchant"),
                eq(Duration.ofMinutes(30))
        );

        verify(valueOperations).set(
                eq("merchant:api-key:" + response.apiKey()),
                contains("ACTIVE"),
                eq(Duration.ofMinutes(30))
        );
    }

    @Test
    void createMerchant_shouldThrowDuplicateMerchantEmailException_whenEmailAlreadyExists() {
        CreateMerchantRequest request = new CreateMerchantRequest(
                "Test Merchant",
                "test-merchant@example.com"
        );

        when(merchantRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> merchantService.createMerchant(request))
                .isInstanceOf(DuplicateMerchantEmailException.class)
                .hasMessageContaining(request.email());

        verify(merchantRepository).existsByEmail(request.email());
        verify(merchantRepository, never()).save(any(Merchant.class));
        verify(merchantRepository, never()).existsByApiKey(anyString());
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void getAllMerchants_shouldReturnMerchantResponses() {
        Merchant firstMerchant = createMerchant(
                UUID.randomUUID(),
                "First Merchant",
                "first@example.com",
                "pk_live_first",
                MerchantStatus.ACTIVE
        );

        Merchant secondMerchant = createMerchant(
                UUID.randomUUID(),
                "Second Merchant",
                "second@example.com",
                "pk_live_second",
                MerchantStatus.PASSIVE
        );

        when(merchantRepository.findAll()).thenReturn(List.of(firstMerchant, secondMerchant));

        List<MerchantResponse> responses = merchantService.getAllMerchants();

        assertThat(responses).hasSize(2);

        assertThat(responses.get(0).id()).isEqualTo(firstMerchant.getId());
        assertThat(responses.get(0).name()).isEqualTo("First Merchant");
        assertThat(responses.get(0).email()).isEqualTo("first@example.com");
        assertThat(responses.get(0).apiKey()).isEqualTo("pk_live_first");
        assertThat(responses.get(0).status()).isEqualTo(MerchantStatus.ACTIVE);

        assertThat(responses.get(1).id()).isEqualTo(secondMerchant.getId());
        assertThat(responses.get(1).name()).isEqualTo("Second Merchant");
        assertThat(responses.get(1).email()).isEqualTo("second@example.com");
        assertThat(responses.get(1).apiKey()).isEqualTo("pk_live_second");
        assertThat(responses.get(1).status()).isEqualTo(MerchantStatus.PASSIVE);
    }

    @Test
    void getMerchantById_shouldReturnMerchantResponse_whenMerchantExists() {
        UUID merchantId = UUID.randomUUID();

        Merchant merchant = createMerchant(
                merchantId,
                "Test Merchant",
                "test@example.com",
                "pk_live_test",
                MerchantStatus.ACTIVE
        );

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        MerchantResponse response = merchantService.getMerchantById(merchantId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(merchantId);
        assertThat(response.name()).isEqualTo("Test Merchant");
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.apiKey()).isEqualTo("pk_live_test");
        assertThat(response.status()).isEqualTo(MerchantStatus.ACTIVE);
    }

    @Test
    void getMerchantById_shouldThrowMerchantNotFoundException_whenMerchantDoesNotExist() {
        UUID merchantId = UUID.randomUUID();

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantService.getMerchantById(merchantId))
                .isInstanceOf(MerchantNotFoundException.class)
                .hasMessageContaining(merchantId.toString());
    }

    @Test
    void updateMerchantStatus_shouldUpdateStatusAndEvictOldApiKeyCache() {
        UUID merchantId = UUID.randomUUID();

        Merchant merchant = createMerchant(
                merchantId,
                "Test Merchant",
                "test@example.com",
                "pk_live_test",
                MerchantStatus.ACTIVE
        );

        UpdateMerchantStatusRequest request =
                new UpdateMerchantStatusRequest(MerchantStatus.PASSIVE);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(merchantRepository.save(any(Merchant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MerchantResponse response = merchantService.updateMerchantStatus(merchantId, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(merchantId);
        assertThat(response.status()).isEqualTo(MerchantStatus.PASSIVE);

        verify(merchantRepository).save(merchant);
        verify(stringRedisTemplate).delete("merchant:api-key:pk_live_test");

        verify(valueOperations, never()).set(
                anyString(),
                anyString(),
                any(Duration.class)
        );
    }

    @Test
    void updateMerchantStatus_shouldCacheApiKeyAgain_whenStatusIsUpdatedToActive() {
        UUID merchantId = UUID.randomUUID();

        Merchant merchant = createMerchant(
                merchantId,
                "Test Merchant",
                "test@example.com",
                "pk_live_test",
                MerchantStatus.PASSIVE
        );

        UpdateMerchantStatusRequest request =
                new UpdateMerchantStatusRequest(MerchantStatus.ACTIVE);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(merchantRepository.save(any(Merchant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MerchantResponse response = merchantService.updateMerchantStatus(merchantId, request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(MerchantStatus.ACTIVE);

        verify(stringRedisTemplate).delete("merchant:api-key:pk_live_test");

        verify(valueOperations).set(
                eq("merchant:api-key:pk_live_test"),
                contains(merchantId.toString()),
                eq(Duration.ofMinutes(30))
        );
    }

    @Test
    void updateMerchantStatus_shouldThrowMerchantNotFoundException_whenMerchantDoesNotExist() {
        UUID merchantId = UUID.randomUUID();

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.empty());

        UpdateMerchantStatusRequest request =
                new UpdateMerchantStatusRequest(MerchantStatus.PASSIVE);

        assertThatThrownBy(() -> merchantService.updateMerchantStatus(merchantId, request))
                .isInstanceOf(MerchantNotFoundException.class)
                .hasMessageContaining(merchantId.toString());

        verify(merchantRepository, never()).save(any(Merchant.class));
    }

    @Test
    void regenerateApiKey_shouldGenerateNewApiKeyEvictOldCacheAndCacheNewApiKey() {
        UUID merchantId = UUID.randomUUID();

        Merchant merchant = createMerchant(
                merchantId,
                "Test Merchant",
                "test@example.com",
                "pk_live_old_key",
                MerchantStatus.ACTIVE
        );

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(merchantRepository.existsByApiKey(anyString())).thenReturn(false);
        when(merchantRepository.save(any(Merchant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GenerateApiKeyResponse response = merchantService.regenerateApiKey(merchantId);

        assertThat(response).isNotNull();
        assertThat(response.merchantId()).isEqualTo(merchantId);
        assertThat(response.apiKey()).startsWith("pk_live_");
        assertThat(response.apiKey()).isNotEqualTo("pk_live_old_key");

        verify(stringRedisTemplate).delete("merchant:api-key:pk_live_old_key");

        verify(valueOperations).set(
                eq("merchant:api-key:" + response.apiKey()),
                contains(merchantId.toString()),
                eq(Duration.ofMinutes(30))
        );

        verify(merchantRepository).save(merchant);
    }

    @Test
    void regenerateApiKey_shouldThrowMerchantNotFoundException_whenMerchantDoesNotExist() {
        UUID merchantId = UUID.randomUUID();

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantService.regenerateApiKey(merchantId))
                .isInstanceOf(MerchantNotFoundException.class)
                .hasMessageContaining(merchantId.toString());

        verify(merchantRepository, never()).save(any(Merchant.class));
        verify(stringRedisTemplate, never()).delete(anyString());
    }

    @Test
    void validateApiKey_shouldReturnInvalidResponse_whenApiKeyIsNull() {
        ApiKeyValidationResponse response = merchantService.validateApiKey(null);

        assertThat(response).isNotNull();
        assertThat(response.valid()).isFalse();
        assertThat(response.merchantId()).isNull();
        assertThat(response.merchantName()).isNull();
        assertThat(response.status()).isNull();

        verify(merchantRepository, never()).findByApiKey(anyString());
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    void validateApiKey_shouldReturnInvalidResponse_whenApiKeyIsBlank() {
        ApiKeyValidationResponse response = merchantService.validateApiKey(" ");

        assertThat(response).isNotNull();
        assertThat(response.valid()).isFalse();
        assertThat(response.merchantId()).isNull();
        assertThat(response.merchantName()).isNull();
        assertThat(response.status()).isNull();

        verify(merchantRepository, never()).findByApiKey(anyString());
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    void validateApiKey_shouldReturnValidResponseFromRedis_whenCacheHitOccurs() {
        UUID merchantId = UUID.randomUUID();
        String apiKey = "pk_live_cached_key";
        String cacheKey = "merchant:api-key:" + apiKey;
        String cachedValue = merchantId + "|Cached Merchant|ACTIVE";

        when(valueOperations.get(cacheKey)).thenReturn(cachedValue);

        ApiKeyValidationResponse response = merchantService.validateApiKey(apiKey);

        assertThat(response).isNotNull();
        assertThat(response.valid()).isTrue();
        assertThat(response.merchantId()).isEqualTo(merchantId);
        assertThat(response.merchantName()).isEqualTo("Cached Merchant");
        assertThat(response.status()).isEqualTo(MerchantStatus.ACTIVE);

        verify(valueOperations).get(cacheKey);
        verify(merchantRepository, never()).findByApiKey(anyString());
    }

    @Test
    void validateApiKey_shouldReturnInvalidResponse_whenCachedValueIsMalformed() {
        String apiKey = "pk_live_malformed_cache_key";
        String cacheKey = "merchant:api-key:" + apiKey;

        when(valueOperations.get(cacheKey)).thenReturn("malformed-cache-value");

        ApiKeyValidationResponse response = merchantService.validateApiKey(apiKey);

        assertThat(response).isNotNull();
        assertThat(response.valid()).isFalse();
        assertThat(response.merchantId()).isNull();
        assertThat(response.merchantName()).isNull();
        assertThat(response.status()).isNull();

        verify(valueOperations).get(cacheKey);
        verify(merchantRepository, never()).findByApiKey(anyString());
    }

    @Test
    void validateApiKey_shouldReturnValidResponseFromDatabaseAndCacheIt_whenCacheMissAndMerchantIsActive() {
        UUID merchantId = UUID.randomUUID();
        String apiKey = "pk_live_db_key";
        String cacheKey = "merchant:api-key:" + apiKey;

        Merchant merchant = createMerchant(
                merchantId,
                "Database Merchant",
                "db@example.com",
                apiKey,
                MerchantStatus.ACTIVE
        );

        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(merchantRepository.findByApiKey(apiKey)).thenReturn(Optional.of(merchant));

        ApiKeyValidationResponse response = merchantService.validateApiKey(apiKey);

        assertThat(response).isNotNull();
        assertThat(response.valid()).isTrue();
        assertThat(response.merchantId()).isEqualTo(merchantId);
        assertThat(response.merchantName()).isEqualTo("Database Merchant");
        assertThat(response.status()).isEqualTo(MerchantStatus.ACTIVE);

        verify(valueOperations).get(cacheKey);
        verify(merchantRepository).findByApiKey(apiKey);

        verify(valueOperations).set(
                eq(cacheKey),
                contains(merchantId.toString()),
                eq(Duration.ofMinutes(30))
        );
    }

    @Test
    void validateApiKey_shouldReturnInvalidResponse_whenCacheMissAndMerchantDoesNotExist() {
        String apiKey = "pk_live_unknown_key";
        String cacheKey = "merchant:api-key:" + apiKey;

        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(merchantRepository.findByApiKey(apiKey)).thenReturn(Optional.empty());

        ApiKeyValidationResponse response = merchantService.validateApiKey(apiKey);

        assertThat(response).isNotNull();
        assertThat(response.valid()).isFalse();
        assertThat(response.merchantId()).isNull();
        assertThat(response.merchantName()).isNull();
        assertThat(response.status()).isNull();

        verify(valueOperations).get(cacheKey);
        verify(merchantRepository).findByApiKey(apiKey);
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void validateApiKey_shouldReturnInvalidResponse_whenCacheMissAndMerchantIsPassive() {
        UUID merchantId = UUID.randomUUID();
        String apiKey = "pk_live_passive_key";
        String cacheKey = "merchant:api-key:" + apiKey;

        Merchant merchant = createMerchant(
                merchantId,
                "Passive Merchant",
                "passive@example.com",
                apiKey,
                MerchantStatus.PASSIVE
        );

        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(merchantRepository.findByApiKey(apiKey)).thenReturn(Optional.of(merchant));

        ApiKeyValidationResponse response = merchantService.validateApiKey(apiKey);

        assertThat(response).isNotNull();
        assertThat(response.valid()).isFalse();
        assertThat(response.merchantId()).isNull();
        assertThat(response.merchantName()).isNull();
        assertThat(response.status()).isNull();

        verify(valueOperations).get(cacheKey);
        verify(merchantRepository).findByApiKey(apiKey);
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void validateApiKey_shouldReturnInvalidResponse_whenCacheMissAndMerchantIsSuspended() {
        UUID merchantId = UUID.randomUUID();
        String apiKey = "pk_live_suspended_key";
        String cacheKey = "merchant:api-key:" + apiKey;

        Merchant merchant = createMerchant(
                merchantId,
                "Suspended Merchant",
                "suspended@example.com",
                apiKey,
                MerchantStatus.SUSPENDED
        );

        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(merchantRepository.findByApiKey(apiKey)).thenReturn(Optional.of(merchant));

        ApiKeyValidationResponse response = merchantService.validateApiKey(apiKey);

        assertThat(response).isNotNull();
        assertThat(response.valid()).isFalse();
        assertThat(response.merchantId()).isNull();
        assertThat(response.merchantName()).isNull();
        assertThat(response.status()).isNull();

        verify(valueOperations).get(cacheKey);
        verify(merchantRepository).findByApiKey(apiKey);
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    private Merchant createMerchant(
            UUID id,
            String name,
            String email,
            String apiKey,
            MerchantStatus status
    ) {
        LocalDateTime now = LocalDateTime.now();

        return Merchant.builder()
                .id(id)
                .name(name)
                .email(email)
                .apiKey(apiKey)
                .status(status)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}