package com.paycore.payment.service;

import com.paycore.payment.domain.OutboxAggregateType;
import com.paycore.payment.domain.OutboxDestinationType;
import com.paycore.payment.domain.OutboxEvent;
import com.paycore.payment.domain.OutboxStatus;
import com.paycore.payment.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxClaimServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxClaimService outboxClaimService;

    @BeforeEach
    void setUp() {
        outboxClaimService = new OutboxClaimService(outboxEventRepository);
    }

    @Test
    void claimBatch_shouldMarkPendingEventsAsProcessing() {
        OutboxEvent first = createPendingEvent();
        OutboxEvent second = createPendingEvent();

        when(outboxEventRepository.findPendingForUpdate(10)).thenReturn(List.of(first, second));
        when(outboxEventRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<OutboxEvent> claimed = outboxClaimService.claimBatch("instance-a", 10);

        assertThat(claimed).hasSize(2);

        assertThat(claimed)
                .allSatisfy(event -> {
                    assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
                    assertThat(event.getLockOwner()).isEqualTo("instance-a");
                    assertThat(event.getProcessingStartedAt()).isNotNull();
                    assertThat(event.getAttemptCount()).isEqualTo(1);
                    assertThat(event.getLastAttemptAt()).isNotNull();
                    assertThat(event.getNextAttemptAt()).isNull();
                });

        verify(outboxEventRepository).findPendingForUpdate(10);
        verify(outboxEventRepository).saveAllAndFlush(List.of(first, second));
    }

    @Test
    void claimBatch_shouldReturnEmptyList_whenNoEventsExist() {
        when(outboxEventRepository.findPendingForUpdate(10)).thenReturn(List.of());

        List<OutboxEvent> result = outboxClaimService.claimBatch("instance-a", 10);

        assertThat(result).isEmpty();

        verify(outboxEventRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void markPublished_shouldReturnTrue_whenOwnedEventWasUpdated() {
        UUID eventId = UUID.randomUUID();

        when(outboxEventRepository.markPublished(
                eq(eventId),
                eq("instance-a"),
                eq(OutboxStatus.PROCESSING),
                eq(OutboxStatus.PUBLISHED),
                any(LocalDateTime.class)
        )).thenReturn(1);

        boolean result = outboxClaimService.markPublished(eventId, "instance-a");

        assertThat(result).isTrue();
    }

    @Test
    void releaseClaim_shouldReturnTrue_whenOwnedEventWasReleased() {
        UUID eventId = UUID.randomUUID();

        when(outboxEventRepository.releaseClaim(
                eventId,
                "instance-a",
                OutboxStatus.PROCESSING,
                OutboxStatus.PENDING
        )).thenReturn(1);

        boolean result = outboxClaimService.releaseClaim(eventId, "instance-a");

        assertThat(result).isTrue();
    }

    @Test
    void releaseStaleClaims_shouldResetExpiredProcessingRows() {
        when(outboxEventRepository.releaseStaleClaims(
                eq(OutboxStatus.PROCESSING),
                eq(OutboxStatus.PENDING),
                any(LocalDateTime.class)
        )).thenReturn(3);

        int released = outboxClaimService.releaseStaleClaims(Duration.ofSeconds(30));

        assertThat(released).isEqualTo(3);
    }

    @Test
    void claimBatch_shouldRejectBlankLockOwner() {
        assertThatThrownBy(() -> outboxClaimService.claimBatch(" ", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Lock owner must not be blank");

        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void claimBatch_shouldRejectInvalidBatchSize() {
        assertThatThrownBy(() -> outboxClaimService.claimBatch("instance-a", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Batch size must be greater than zero");

        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void registerFailure_shouldReturnEventToPendingWithRetryMetadata() {
        UUID eventId = UUID.randomUUID();
        LocalDateTime nextAttemptAt = LocalDateTime.now().plusSeconds(10);

        when(outboxEventRepository.registerFailure(
                eventId,
                "instance-a",
                OutboxStatus.PROCESSING,
                OutboxStatus.PENDING,
                nextAttemptAt,
                "TimeoutException: Kafka unavailable"
        )).thenReturn(1);

        boolean result = outboxClaimService.registerFailure(eventId, "instance-a", nextAttemptAt, "TimeoutException: Kafka unavailable");

        assertThat(result).isTrue();

        verify(outboxEventRepository).registerFailure(
                eventId,
                "instance-a",
                OutboxStatus.PROCESSING,
                OutboxStatus.PENDING,
                nextAttemptAt,
                "TimeoutException: Kafka unavailable"
        );
    }

    private OutboxEvent createPendingEvent() {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType(OutboxAggregateType.PAYMENT)
                .aggregateId(UUID.randomUUID())
                .eventType("PAYMENT_AUTHORIZED")
                .destinationType(OutboxDestinationType.KAFKA)
                .destination("payment-events")
                .messageKey(UUID.randomUUID().toString())
                .payload("{\"eventId\":\"test\"}")
                .status(OutboxStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
