package com.paycore.ledger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_ledger_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentLedgerEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID eventId;

    @Column(nullable = false, length = 80)
    private String eventType;

    @Column(nullable = false)
    private UUID paymentId;

    @Column(nullable = false)
    private UUID merchantId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 100)
    private String orderId;

    @Column(nullable = false, length = 40)
    private String paymentStatus;

    @Column(nullable = false, length = 60)
    private String providerType;

    @Column(length = 120)
    private String providerReferenceId;

    @Column(length = 30)
    private String providerResponseCode;

    @Column(length = 255)
    private String providerResponseMessage;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false)
    private LocalDateTime consumedAt;
}