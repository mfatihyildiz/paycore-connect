package com.paycore.payment.exception;

public class InvalidMerchantApiKeyException extends RuntimeException {

    public InvalidMerchantApiKeyException() {
        super("Invalid or inactive merchant API key");
    }
}