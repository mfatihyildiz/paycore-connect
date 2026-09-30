package com.paycore.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherScheduler {

    private final OutboxPublisherService outboxPublisherService;

    @Scheduled(fixedDelayString = "${paycore.outbox.poll-interval-ms:1000}")
    public void publishPendingOutboxEvents() {
        try {
            int published = outboxPublisherService.publishPendingBatch();

            if (published > 0) {
                log.debug("Published {} outbox event(s)", published);
            }
        } catch (Exception exception) {
            log.error("Outbox publisher cycle failed", exception);
        }
    }
}
