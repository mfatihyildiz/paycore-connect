package com.paycore.payment.repository;

import com.paycore.payment.domain.OutboxEvent;
import com.paycore.payment.domain.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
    SELECT e.*
    FROM outbox_events e
    WHERE e.status = 'PENDING'
      AND (e.next_attempt_at IS NULL OR e.next_attempt_at <= CURRENT_TIMESTAMP)
      AND NOT EXISTS (
          SELECT 1
          FROM outbox_events earlier
          WHERE earlier.aggregate_type = e.aggregate_type
            AND earlier.aggregate_id = e.aggregate_id
            AND earlier.destination_type = e.destination_type
            AND earlier.destination = e.destination
            AND earlier.status <> 'PUBLISHED'
            AND (
                earlier.created_at < e.created_at
                OR (
                    earlier.created_at = e.created_at
                    AND earlier.id < e.id
                )
            )
      )
    ORDER BY e.created_at ASC, e.id ASC
    LIMIT :batchSize
    FOR UPDATE OF e SKIP LOCKED
    """, nativeQuery = true)
    List<OutboxEvent> findPendingForUpdate(@Param("batchSize") int batchSize);

    @Modifying
    @Query("""
    UPDATE OutboxEvent e
    SET e.status = :publishedStatus,
        e.publishedAt = :publishedAt,
        e.processingStartedAt = null,
        e.lockOwner = null,
        e.nextAttemptAt = null,
        e.lastError = null
    WHERE e.id = :id
      AND e.status = :processingStatus
      AND e.lockOwner = :lockOwner
    """)
    int markPublished(
            @Param("id") UUID id,
            @Param("lockOwner") String lockOwner,
            @Param("processingStatus") OutboxStatus processingStatus,
            @Param("publishedStatus") OutboxStatus publishedStatus,
            @Param("publishedAt") LocalDateTime publishedAt
    );

    @Modifying
    @Query("""
        UPDATE OutboxEvent e
        SET e.status = :pendingStatus,
            e.processingStartedAt = null,
            e.lockOwner = null
        WHERE e.id = :id
          AND e.status = :processingStatus
          AND e.lockOwner = :lockOwner
        """)
    int releaseClaim(
            @Param("id") UUID id,
            @Param("lockOwner") String lockOwner,
            @Param("processingStatus") OutboxStatus processingStatus,
            @Param("pendingStatus") OutboxStatus pendingStatus
    );

    @Modifying
    @Query("""
        UPDATE OutboxEvent e
        SET e.status = :pendingStatus,
            e.processingStartedAt = null,
            e.lockOwner = null
        WHERE e.status = :processingStatus
          AND e.processingStartedAt < :cutoff
        """)
    int releaseStaleClaims(
            @Param("processingStatus") OutboxStatus processingStatus,
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("cutoff") LocalDateTime cutoff
    );

    @Modifying
    @Query("""
    UPDATE OutboxEvent e
    SET e.status = :pendingStatus,
        e.processingStartedAt = null,
        e.lockOwner = null,
        e.nextAttemptAt = :nextAttemptAt,
        e.lastError = :lastError
    WHERE e.id = :id
      AND e.status = :processingStatus
      AND e.lockOwner = :lockOwner
    """)
    int registerFailure(
            @Param("id") UUID id,
            @Param("lockOwner") String lockOwner,
            @Param("processingStatus") OutboxStatus processingStatus,
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("lastError") String lastError
    );
}
