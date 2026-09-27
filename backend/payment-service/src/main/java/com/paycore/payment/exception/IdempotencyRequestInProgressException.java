package com.paycore.payment.exception;

public class IdempotencyRequestInProgressException extends RuntimeException {

    public IdempotencyRequestInProgressException() {
        super("A request with this idempotency key is already being processed");
    }
}
