package com.paycore.settlement.service;

import com.paycore.settlement.event.PaymentEvent;
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

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Testcontainers
class SettlementConsumerIdempotencyIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paycore_settlement_test")
            .withUsername("paycore")
            .withPassword("paycore");

    @Autowired
    private SettlementService settlementService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void processPaymentEvent_shouldPersistExactlyOnce_whenSameEventIsProcessedConcurrently() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        PaymentEvent event = createAuthorizedEvent(eventId, paymentId, merchantId, "ORDER-SAME-EVENT");

        runConcurrently(10, () -> settlementService.processPaymentEvent(event));

        Long paymentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM settlements WHERE payment_id = ?", Long.class, paymentId);
        Long eventCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM settlements WHERE event_id = ?", Long.class, eventId);

        assertThat(paymentCount).isEqualTo(1L);
        assertThat(eventCount).isEqualTo(1L);
    }

    @Test
    void processPaymentEvent_shouldPersistOneSettlement_whenDifferentEventsTargetSamePaymentConcurrently() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        List<PaymentEvent> events = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            events.add(createAuthorizedEvent(UUID.randomUUID(), paymentId, merchantId, "ORDER-SAME-PAYMENT"));
        }

        List<Runnable> actions = events.stream().map(event -> (Runnable) () -> settlementService.processPaymentEvent(event)).toList();

        runConcurrently(actions);

        Long paymentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM settlements WHERE payment_id = ?", Long.class, paymentId);

        assertThat(paymentCount).isEqualTo(1L);

        UUID storedEventId = jdbcTemplate.queryForObject("SELECT event_id FROM settlements WHERE payment_id = ?", UUID.class, paymentId);

        assertThat(events).extracting(PaymentEvent::eventId).contains(storedEventId);
    }

    private PaymentEvent createAuthorizedEvent(UUID eventId, UUID paymentId, UUID merchantId, String orderId) {
        return new PaymentEvent(
                eventId,
                "PAYMENT_AUTHORIZED",
                paymentId,
                merchantId,
                new BigDecimal("1000.00"),
                "TRY",
                orderId,
                "AUTHORIZED",
                "MOCK_BANK",
                "PROVIDER-REF-" + eventId,
                "00",
                "APPROVED",
                LocalDateTime.now()
        );
    }

    private void runConcurrently(int concurrency, Runnable action) throws Exception {
        List<Runnable> actions = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            actions.add(action);
        }

        runConcurrently(actions);
    }

    private void runConcurrently(List<? extends Runnable> actions) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(actions.size());
        CountDownLatch ready = new CountDownLatch(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (Runnable action : actions) {
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
