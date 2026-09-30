package com.paycore.payment.service;

import com.paycore.payment.domain.OutboxDestinationType;
import com.paycore.payment.domain.OutboxEvent;
import com.paycore.payment.event.PaymentEventProducer;
import com.paycore.payment.notification.PaymentNotificationProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class OutboxPublisherService {

    private final OutboxClaimService outboxClaimService;
    private final PaymentEventProducer paymentEventProducer;
    private final PaymentNotificationProducer paymentNotificationProducer;

    private final String lockOwner = "payment-service-" + UUID.randomUUID();

    @Value("${paycore.outbox.batch-size:10}")
    private int batchSize;

    @Value("${paycore.outbox.lease-timeout-seconds:120}")
    private long leaseTimeoutSeconds;

    @Value("${paycore.outbox.publish-timeout-seconds:5}")
    private long publishTimeoutSeconds;

    @Value("${paycore.outbox.retry-base-delay-seconds:5}")
    private long retryBaseDelaySeconds;

    @Value("${paycore.outbox.retry-max-delay-seconds:60}")
    private long retryMaxDelaySeconds;

    public OutboxPublisherService(
            OutboxClaimService outboxClaimService,
            PaymentEventProducer paymentEventProducer,
            PaymentNotificationProducer paymentNotificationProducer
    ) {
        this.outboxClaimService = outboxClaimService;
        this.paymentEventProducer = paymentEventProducer;
        this.paymentNotificationProducer = paymentNotificationProducer;
    }

    public int publishPendingBatch() {
        outboxClaimService.releaseStaleClaims(Duration.ofSeconds(leaseTimeoutSeconds));

        List<OutboxEvent> events = outboxClaimService.claimBatch(lockOwner, batchSize);

        int publishedCount = 0;

        for (OutboxEvent event : events) {
            if (publishEvent(event)) {
                publishedCount++;
            }
        }

        return publishedCount;
    }

    private boolean publishEvent(OutboxEvent event) {
        try {
            dispatch(event);

            boolean markedPublished = outboxClaimService.markPublished(event.getId(), lockOwner);

            if (!markedPublished) {
                log.warn("Outbox event was published but could not be marked as PUBLISHED. eventId={}", event.getId());

                return false;
            }

            log.debug(
                    "Outbox event published successfully. eventId={}, destinationType={}",
                    event.getId(),
                    event.getDestinationType()
            );

            return true;

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            releaseClaimSafely(event);

            log.warn("Outbox publishing interrupted. eventId={}", event.getId());

            return false;

        } catch (Exception exception) {
            registerFailureSafely(event, exception);
            return false;

        }
    }

    private void dispatch(OutboxEvent event) throws Exception {
        if (event.getDestinationType() == OutboxDestinationType.KAFKA) {
            publishKafka(event);
            return;
        }

        if (event.getDestinationType() == OutboxDestinationType.RABBITMQ) {
            publishRabbitMq(event);
            return;
        }

        throw new IllegalStateException("Unsupported outbox destination type: " + event.getDestinationType());
    }

    private void publishKafka(OutboxEvent event) throws Exception {
        paymentEventProducer.publish(
                event.getDestination(),
                event.getMessageKey(),
                event.getPayload()
        ).get(
                publishTimeoutSeconds,
                TimeUnit.SECONDS
        );
    }

    private void publishRabbitMq(OutboxEvent event) throws Exception {
        paymentNotificationProducer.publish(
                event.getDestination(),
                event.getRoutingKey(),
                event.getPayload(),
                event.getId()
        ).get(
                publishTimeoutSeconds,
                TimeUnit.SECONDS
        );
    }

    private void releaseClaimSafely(OutboxEvent event) {
        try {
            boolean released = outboxClaimService.releaseClaim(event.getId(), lockOwner);

            if (!released) {
                log.warn("Outbox claim could not be released. eventId={}", event.getId());
            }
        } catch (Exception releaseException) {
            log.error("Failed to release outbox claim. eventId={}", event.getId(), releaseException
            );
        }
    }

    private void registerFailureSafely(OutboxEvent event, Exception exception) {
        try {
            Duration backoff = calculateBackoff(event.getAttemptCount());
            LocalDateTime nextAttemptAt = LocalDateTime.now().plus(backoff);
            String error = summarizeException(exception);

            boolean registered = outboxClaimService.registerFailure(event.getId(), lockOwner, nextAttemptAt, error);

            if (!registered) {
                log.warn("Outbox failure could not be registered. eventId={}", event.getId());
                return;
            }

            log.warn(
                    "Outbox event publish failed. eventId={}, destinationType={}, attempt={}, retryIn={}s, reason={}",
                    event.getId(),
                    event.getDestinationType(),
                    event.getAttemptCount(),
                    backoff.toSeconds(),
                    error
            );
        } catch (Exception registrationException) {
            log.error("Failed to register outbox publish failure. eventId={}", event.getId(), registrationException);
        }
    }

    private Duration calculateBackoff(int attemptCount) {
        long delay = retryBaseDelaySeconds;

        for (int attempt = 1; attempt < attemptCount && delay < retryMaxDelaySeconds; attempt++) {
            delay = Math.min(delay * 2, retryMaxDelaySeconds);
        }

        return Duration.ofSeconds(delay);
    }

    private String summarizeException(Throwable throwable) {
        Throwable rootCause = throwable;

        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        String message = rootCause.getMessage();
        String summary = rootCause.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);

        return summary.length() <= 2000 ? summary : summary.substring(0, 2000);
    }
}
