package com.paycore.payment.exception;

import java.util.UUID;

public class DuplicateOrderException extends RuntimeException {

    public DuplicateOrderException(UUID merchantId, String orderId) {
        super("Payment already exists for merchant " + merchantId + " and order id " + orderId);
    }
}