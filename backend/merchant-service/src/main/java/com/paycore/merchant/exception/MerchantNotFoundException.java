package com.paycore.merchant.exception;

import java.util.UUID;

public class MerchantNotFoundException extends RuntimeException {

    public MerchantNotFoundException(UUID merchantId) {
        super("Merchant not found with id: " + merchantId);
    }
}