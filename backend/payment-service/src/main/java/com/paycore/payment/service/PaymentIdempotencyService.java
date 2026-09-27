package com.paycore.payment.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.paycore.payment.domain.IdempotencyOperation;
import com.paycore.payment.domain.IdempotencyRecord;
import com.paycore.payment.domain.IdempotencyStatus;
import com.paycore.payment.dto.PaymentResponse;
import com.paycore.payment.exception.IdempotencyKeyReuseException;
import com.paycore.payment.exception.IdempotencyRequestInProgressException;
import com.paycore.payment.exception.InvalidIdempotencyKeyException;
import com.paycore.payment.repository.IdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentIdempotencyService {

    private static final int MAX_KEY_LENGTH = 255;

    private final IdempotencyRepository idempotencyRepository;
    private final JsonMapper jsonMapper;

    public void validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidIdempotencyKeyException("Idempotency-Key must not be blank");
        }

        if (idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new InvalidIdempotencyKeyException("Idempotency-Key must not exceed " + MAX_KEY_LENGTH + " characters");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyRecord acquire(
            UUID merchantId,
            IdempotencyOperation operation,
            String idempotencyKey,
            String requestHash
    ) {
        int inserted = idempotencyRepository.tryInsertProcessing(
                UUID.randomUUID(),
                merchantId,
                operation.name(),
                idempotencyKey,
                requestHash
        );

        IdempotencyRecord record = idempotencyRepository.findByMerchantIdAndOperationAndIdempotencyKey(
                merchantId,
                operation,
                idempotencyKey
        ).orElseThrow(() -> new IllegalStateException("Idempotency record could not be loaded"));

        if (inserted == 1) {
            return record;
        }

        if (!record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyReuseException();
        }

        if (record.getStatus() == IdempotencyStatus.COMPLETED) {
            return record;
        }

        throw new IdempotencyRequestInProgressException();
    }

    public PaymentResponse replay(IdempotencyRecord record) {
        if (record.getStatus() != IdempotencyStatus.COMPLETED || record.getResponseBody() == null) {
            throw new IllegalStateException("Idempotency record cannot be replayed");
        }

        try {
            return jsonMapper.readValue(record.getResponseBody(), PaymentResponse.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored idempotency response could not be deserialized", exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID idempotencyRecordId, PaymentResponse response) {
        IdempotencyRecord record = idempotencyRepository
                .findById(idempotencyRecordId)
                .orElseThrow(() -> new IllegalStateException("Idempotency record not found"));

        if (record.getStatus() == IdempotencyStatus.COMPLETED) {
            return;
        }

        try {
            record.setResponseBody(jsonMapper.writeValueAsString(response));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Payment response could not be serialized", exception);
        }

        record.setPaymentId(response.id());
        record.setStatus(IdempotencyStatus.COMPLETED);
        record.setCompletedAt(LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(UUID idempotencyRecordId) {

        IdempotencyRecord record = idempotencyRepository.findById(idempotencyRecordId).orElse(null);

        if (record == null) {
            return;
        }

        if (record.getStatus() == IdempotencyStatus.PROCESSING) {
            idempotencyRepository.delete(record);
        }
    }
}
