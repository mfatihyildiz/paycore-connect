package com.paycore.settlement.exception;

import java.util.UUID;

public class SettlementNotFoundException extends RuntimeException {

    public SettlementNotFoundException(UUID paymentId) {
        super("Settlement not found for payment id: " + paymentId);
    }
}