package com.paycore.payment.exception;

public class FraudRejectedPaymentException extends RuntimeException {

    public FraudRejectedPaymentException(String message) {
        super(message);
    }
}