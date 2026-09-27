package com.paycore.payment.idempotency;

import com.paycore.payment.dto.PaymentInitiateRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class PaymentRequestFingerprint {

    public String calculate(PaymentInitiateRequest request) {
        String canonicalRequest = encode(request.amount()
                .stripTrailingZeros().toPlainString()) + encode(request.currency().toUpperCase(Locale.ROOT)) +
                encode(request.orderId()) + encode(request.cardToken()) + encode(request.providerType().name());

        return sha256(canonicalRequest);
    }

    private String encode(String value) {
        return value.length() + ":" + value;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
