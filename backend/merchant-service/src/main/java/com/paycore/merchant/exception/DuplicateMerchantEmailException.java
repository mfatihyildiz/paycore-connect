package com.paycore.merchant.exception;

public class DuplicateMerchantEmailException extends RuntimeException {

    public DuplicateMerchantEmailException(String email) {
        super("Merchant already exists with email: " + email);
    }
}