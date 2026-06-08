package com.paycore.notification.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID paymentId;

    @Column(nullable = false)
    private UUID merchantId;

    @Column(nullable = false, length = 150)
    private String merchantName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType notificationType;

    @Column(nullable = false, length = 40)
    private String paymentStatus;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 100)
    private String orderId;

    @Column(length = 120)
    private String providerReferenceId;

    @Column(length = 30)
    private String providerResponseCode;

    @Column(length = 255)
    private String providerResponseMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationStatus status;

    @Column(nullable = false, length = 500)
    private String simulatedTarget;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    private LocalDateTime sentAt;

    @PrePersist
    public void prePersist() {
        if (this.receivedAt == null) {
            this.receivedAt = LocalDateTime.now();
        }

        if (this.status == null) {
            this.status = NotificationStatus.RECEIVED;
        }
    }
}