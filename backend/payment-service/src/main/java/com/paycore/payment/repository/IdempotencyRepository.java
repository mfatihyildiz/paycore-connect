package com.paycore.payment.repository;

import com.paycore.payment.domain.IdempotencyOperation;
import com.paycore.payment.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByMerchantIdAndOperationAndIdempotencyKey(
            UUID merchantId,
            IdempotencyOperation operation,
            String idempotencyKey
    );

    @Modifying
    @Query(value = """
                INSERT INTO idempotency_records (
                    id,
                    merchant_id,
                    operation,
                    idempotency_key,  
                    request_hash,
                    status,
                    created_at
                )
                VALUES (
                    :id,
                    :merchantId,
                    :operation, 
                    :idempotencyKey,
                    :requestHash,
                    'PROCESSING',
                    CURRENT_TIMESTAMP
                )
                ON CONFLICT (
                    merchant_id,
                    operation,
                    idempotency_key
                )
                DO NOTHING
                """, nativeQuery = true
    )
    int tryInsertProcessing(
            @Param("id") UUID id,
            @Param("merchantId") UUID merchantId,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash
    );
}
