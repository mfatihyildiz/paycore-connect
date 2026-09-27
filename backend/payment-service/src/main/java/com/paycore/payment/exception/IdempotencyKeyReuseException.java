package com.paycore.payment.exception;

public class IdempotencyKeyReuseException extends RuntimeException {

    public IdempotencyKeyReuseException() {
        super("Idempotency key has already been used with different request parameters");
    }
}
