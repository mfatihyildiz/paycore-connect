package com.paycore.ledger.repository;

import com.paycore.ledger.domain.PaymentLedgerEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentLedgerEventWriter {

    private static final String INSERT_IF_ABSENT_SQL = """
            INSERT INTO payment_ledger_events (
                id,
                event_id,
                event_type,
                payment_id,
                merchant_id,
                amount,
                currency,
                order_id,
                payment_status,
                provider_type,
                provider_reference_id,
                provider_response_code,
                provider_response_message,
                occurred_at,
                consumed_at
            )
            VALUES (
                :id,
                :eventId,
                :eventType,
                :paymentId,
                :merchantId,
                :amount,
                :currency,
                :orderId,
                :paymentStatus,
                :providerType,
                :providerReferenceId,
                :providerResponseCode,
                :providerResponseMessage,
                :occurredAt,
                :consumedAt
            )
            ON CONFLICT (event_id) DO NOTHING
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public boolean insertIfAbsent(PaymentLedgerEvent event) {
        return jdbcTemplate.update(INSERT_IF_ABSENT_SQL, new BeanPropertySqlParameterSource(event)) == 1;
    }
}
