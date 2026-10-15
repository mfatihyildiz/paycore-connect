package com.paycore.merchant.integration;

import com.paycore.merchant.dto.CreateMerchantRequest;
import com.paycore.merchant.exception.DuplicateMerchantEmailException;
import com.paycore.merchant.service.MerchantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false"})
@Testcontainers
class MerchantConcurrencyIT {

    private static final int THREAD_COUNT = 10;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paycore_merchant_test").withUsername("paycore").withPassword("paycore");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM merchants");

        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void createMerchant_shouldCreateExactlyOneMerchant_whenSameEmailArrivesConcurrently() throws Exception {
        String email = "concurrent-" + UUID.randomUUID() + "@example.com";

        CreateMerchantRequest request = new CreateMerchantRequest("Concurrent Merchant", email);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<CreateResult>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < THREAD_COUNT; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();

                    if (!startLatch.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for concurrent start");
                    }

                    try {
                        merchantService.createMerchant(request);
                        return CreateResult.CREATED;
                    } catch (DuplicateMerchantEmailException ex) {
                        return CreateResult.DUPLICATE;
                    }
                }));
            }

            assertThat(readyLatch.await(10, TimeUnit.SECONDS)).isTrue();

            startLatch.countDown();

            int createdCount = 0;
            int duplicateCount = 0;

            for (Future<CreateResult> future : futures) {
                CreateResult result = future.get(20, TimeUnit.SECONDS);

                if (result == CreateResult.CREATED) {
                    createdCount++;
                } else if (result == CreateResult.DUPLICATE) {
                    duplicateCount++;
                }
            }

            Long databaseCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM merchants WHERE email = ?", Long.class, email);

            assertThat(createdCount).isEqualTo(1);
            assertThat(duplicateCount).isEqualTo(THREAD_COUNT - 1);
            assertThat(databaseCount).isEqualTo(1L);

        } finally {
            startLatch.countDown();
            executor.shutdownNow();
        }
    }

    private enum CreateResult {
        CREATED,
        DUPLICATE
    }
}
