package com.paycore.settlement.repository;

import com.paycore.settlement.domain.Settlement;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SettlementWriter {

    private static final String INSERT_IF_ABSENT_SQL = """
            INSERT INTO settlements (
                id,
                event_id,
                payment_id,
                merchant_id,
                gross_amount,
                commission_rate,
                commission_amount,
                net_amount,
                currency,
                status,
                settlement_date,
                order_id,
                source_event_type,
                source_event_occurred_at,
                created_at,
                updated_at
            )
            VALUES (
                :id,
                :eventId,
                :paymentId,
                :merchantId,
                :grossAmount,
                :commissionRate,
                :commissionAmount,
                :netAmount,
                :currency,
                :status,
                :settlementDate,
                :orderId,
                :sourceEventType,
                :sourceEventOccurredAt,
                :createdAt,
                :updatedAt
            )
            ON CONFLICT DO NOTHING
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public boolean insertIfAbsent(Settlement settlement) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", settlement.getId())
                .addValue("eventId", settlement.getEventId())
                .addValue("paymentId", settlement.getPaymentId())
                .addValue("merchantId", settlement.getMerchantId())
                .addValue("grossAmount", settlement.getGrossAmount())
                .addValue("commissionRate", settlement.getCommissionRate())
                .addValue("commissionAmount", settlement.getCommissionAmount())
                .addValue("netAmount", settlement.getNetAmount())
                .addValue("currency", settlement.getCurrency())
                .addValue("status", settlement.getStatus().name())
                .addValue("settlementDate", settlement.getSettlementDate())
                .addValue("orderId", settlement.getOrderId())
                .addValue("sourceEventType", settlement.getSourceEventType())
                .addValue("sourceEventOccurredAt", settlement.getSourceEventOccurredAt())
                .addValue("createdAt", settlement.getCreatedAt())
                .addValue("updatedAt", settlement.getUpdatedAt());

        return jdbcTemplate.update(INSERT_IF_ABSENT_SQL, params) == 1;
    }
}
