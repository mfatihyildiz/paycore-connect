package com.paycore.payment.domain;

public enum PaymentStatus {
    INITIATED,
    AUTHORIZED,
    FAILED,
    REFUNDED,
    SETTLED
}