package com.paycore.ledger.exception;

import java.util.UUID;

public class PaymentLedgerNotFoundException extends RuntimeException {

    public PaymentLedgerNotFoundException(UUID paymentId) {
        super("No ledger events found for payment id: " + paymentId);
    }
}