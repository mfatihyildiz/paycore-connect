package com.paycore.merchant.integration;

import com.paycore.merchant.domain.Merchant;
import com.paycore.merchant.domain.MerchantStatus;
import com.paycore.merchant.dto.ApiKeyValidationResponse;
import com.paycore.merchant.dto.CreateMerchantRequest;
import com.paycore.merchant.dto.GenerateApiKeyResponse;
import com.paycore.merchant.dto.MerchantResponse;
import com.paycore.merchant.dto.UpdateMerchantStatusRequest;
import com.paycore.merchant.repository.MerchantRepository;
import com.paycore.merchant.service.MerchantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false"})
@Testcontainers
class MerchantCacheConsistencyIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paycore_merchant_test")
            .withUsername("paycore")
            .withPassword("paycore");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private MerchantRepository merchantRepository;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        merchantRepository.deleteAllInBatch();

        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void createMerchant_shouldCommitToDatabase_whenRedisWriteFailsAfterCommit() {
        CreateMerchantRequest request = new CreateMerchantRequest("Redis Failure Merchant", "redis-failure@example.com");

        doThrow(new RedisConnectionFailureException("Redis unavailable")).when(valueOperations).set(anyString(), anyString(), any());

        MerchantResponse response = merchantService.createMerchant(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();

        Optional<Merchant> storedMerchant = merchantRepository.findById(response.id());

        assertThat(storedMerchant).isPresent();
        assertThat(storedMerchant.get().getEmail()).isEqualTo("redis-failure@example.com");
        assertThat(storedMerchant.get().getStatus()).isEqualTo(MerchantStatus.ACTIVE);

        assertThat(merchantRepository.count()).isEqualTo(1);
    }

    @Test
    void validateApiKey_shouldRejectStaleActiveCache_afterMerchantIsSuspendedEvenWhenEvictionFails() {
        Merchant merchant = Merchant.builder()
                .name("Suspended Merchant")
                .email("suspended@example.com")
                .apiKey("pk_live_suspension_test")
                .status(MerchantStatus.ACTIVE)
                .build();

        merchant = merchantRepository.saveAndFlush(merchant);

        UUID merchantId = merchant.getId();
        String apiKey = merchant.getApiKey();
        String cacheKey = "merchant:api-key:" + apiKey;

        String staleCacheValue = merchantId + "|Suspended Merchant|ACTIVE";

        when(valueOperations.get(cacheKey)).thenReturn(staleCacheValue);
        doThrow(new RedisConnectionFailureException("Redis unavailable")).when(stringRedisTemplate).delete(cacheKey);

        MerchantResponse updated = merchantService.updateMerchantStatus(merchantId, new UpdateMerchantStatusRequest(MerchantStatus.SUSPENDED));
        assertThat(updated.status()).isEqualTo(MerchantStatus.SUSPENDED);

        Merchant storedMerchant = merchantRepository.findById(merchantId).orElseThrow();
        assertThat(storedMerchant.getStatus()).isEqualTo(MerchantStatus.SUSPENDED);

        ApiKeyValidationResponse validation = merchantService.validateApiKey(apiKey);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.merchantId()).isNull();
        assertThat(validation.merchantName()).isNull();
        assertThat(validation.status()).isNull();

        verify(stringRedisTemplate, atLeastOnce()).delete(cacheKey);
    }

    @Test
    void validateApiKey_shouldRejectOldStaleKey_afterRotationEvenWhenEvictionFails() {
        Merchant merchant = Merchant.builder()
                .name("Rotation Merchant")
                .email("rotation@example.com")
                .apiKey("pk_live_old_rotation_key")
                .status(MerchantStatus.ACTIVE)
                .build();

        merchant = merchantRepository.saveAndFlush(merchant);

        UUID merchantId = merchant.getId();
        String oldApiKey = merchant.getApiKey();
        String oldCacheKey = "merchant:api-key:" + oldApiKey;

        String staleCacheValue = merchantId + "|Rotation Merchant|ACTIVE";
        when(valueOperations.get(oldCacheKey)).thenReturn(staleCacheValue);

        doThrow(new RedisConnectionFailureException("Redis unavailable")).when(stringRedisTemplate).delete(oldCacheKey);

        GenerateApiKeyResponse generated = merchantService.regenerateApiKey(merchantId);
        assertThat(generated.apiKey()).isNotEqualTo(oldApiKey);
        assertThat(generated.apiKey()).startsWith("pk_live_");

        Merchant storedMerchant = merchantRepository.findById(merchantId).orElseThrow();
        assertThat(storedMerchant.getApiKey()).isEqualTo(generated.apiKey());

        ApiKeyValidationResponse oldKeyValidation = merchantService.validateApiKey(oldApiKey);
        assertThat(oldKeyValidation.valid()).isFalse();

        String newCacheKey = "merchant:api-key:" + generated.apiKey();
        when(valueOperations.get(newCacheKey)).thenReturn(null);

        ApiKeyValidationResponse newKeyValidation = merchantService.validateApiKey(generated.apiKey());
        assertThat(newKeyValidation.valid()).isTrue();
        assertThat(newKeyValidation.merchantId()).isEqualTo(merchantId);
        assertThat(newKeyValidation.status()).isEqualTo(MerchantStatus.ACTIVE);
    }
}
