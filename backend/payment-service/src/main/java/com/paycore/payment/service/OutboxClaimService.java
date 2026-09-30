package com.paycore.payment.service;

import com.paycore.payment.domain.OutboxEvent;
import com.paycore.payment.domain.OutboxStatus;
import com.paycore.payment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxClaimService {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public List<OutboxEvent> claimBatch(String lockOwner, int batchSize) {
        validateLockOwner(lockOwner);

        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be greater than zero");
        }

        List<OutboxEvent> events = outboxEventRepository.findPendingForUpdate(batchSize);

        if (events.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();

        for (OutboxEvent event : events) {
            event.setStatus(OutboxStatus.PROCESSING);
            event.setProcessingStartedAt(now);
            event.setLockOwner(lockOwner);
            event.setAttemptCount(event.getAttemptCount() + 1);
            event.setLastAttemptAt(now);
            event.setNextAttemptAt(null);
        }

        return outboxEventRepository.saveAllAndFlush(events);
    }

    @Transactional
    public boolean markPublished(UUID eventId, String lockOwner) {
        validateLockOwner(lockOwner);

        return outboxEventRepository.markPublished(
                eventId,
                lockOwner,
                OutboxStatus.PROCESSING,
                OutboxStatus.PUBLISHED,
                LocalDateTime.now()
        ) == 1;
    }

    @Transactional
    public boolean releaseClaim(UUID eventId, String lockOwner) {
        validateLockOwner(lockOwner);

        return outboxEventRepository.releaseClaim(
                eventId,
                lockOwner,
                OutboxStatus.PROCESSING,
                OutboxStatus.PENDING
        ) == 1;
    }

    @Transactional
    public int releaseStaleClaims(Duration leaseTimeout) {
        if (leaseTimeout == null || leaseTimeout.isZero() || leaseTimeout.isNegative()) {
            throw new IllegalArgumentException("Lease timeout must be greater than zero");
        }

        LocalDateTime cutoff = LocalDateTime.now().minus(leaseTimeout);

        return outboxEventRepository.releaseStaleClaims(OutboxStatus.PROCESSING, OutboxStatus.PENDING, cutoff);
    }

    @Transactional
    public boolean registerFailure(UUID eventId, String lockOwner, LocalDateTime nextAttemptAt, String lastError) {
        validateLockOwner(lockOwner);

        if (nextAttemptAt == null) {
            throw new IllegalArgumentException("Next attempt time must not be null");
        }

        return outboxEventRepository.registerFailure(eventId, lockOwner, OutboxStatus.PROCESSING, OutboxStatus.PENDING, nextAttemptAt, lastError) == 1;
    }

    private void validateLockOwner(String lockOwner) {
        if (lockOwner == null || lockOwner.isBlank()) {
            throw new IllegalArgumentException("Lock owner must not be blank");
        }
    }
}
