package com.paycore.notification.service;

import com.paycore.notification.dto.PaymentNotificationMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@Testcontainers
class NotificationConsumerIdempotencyIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paycore_notification_test")
            .withUsername("paycore")
            .withPassword("paycore");

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void processPaymentNotification_shouldPersistExactlyOnce_whenSameNotificationIsProcessedConcurrently()
            throws Exception {

        UUID notificationId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        PaymentNotificationMessage message = new PaymentNotificationMessage(
                notificationId,
                paymentId,
                merchantId,
                "Concurrent Test Merchant",
                new BigDecimal("1250.00"),
                "TRY",
                "ORDER-NOTIFICATION-CONCURRENT-1001",
                "AUTHORIZED",
                "MOCK_BANK",
                "PROVIDER-REF-CONCURRENT",
                "00",
                "APPROVED",
                LocalDateTime.now()
        );

        runConcurrently(10, () -> notificationService.processPaymentNotification(message));

        Long notificationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_logs WHERE notification_id = ?",
                Long.class,
                notificationId
        );

        assertThat(notificationCount).isEqualTo(1L);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM notification_logs WHERE notification_id = ?",
                String.class,
                notificationId
        );

        assertThat(status).isEqualTo("SENT");

        LocalDateTime sentAt = jdbcTemplate.queryForObject(
                "SELECT sent_at FROM notification_logs WHERE notification_id = ?",
                LocalDateTime.class,
                notificationId
        );

        assertThat(sentAt).isNotNull();

        UUID storedPaymentId = jdbcTemplate.queryForObject(
                "SELECT payment_id FROM notification_logs WHERE notification_id = ?",
                UUID.class,
                notificationId
        );

        assertThat(storedPaymentId).isEqualTo(paymentId);
    }

    private void runConcurrently(int concurrency, Runnable action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < concurrency; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();

                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent workers were not released in time");
                    }

                    action.run();
                    return null;
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            start.countDown();

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
