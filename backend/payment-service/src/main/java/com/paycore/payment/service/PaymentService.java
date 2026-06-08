package com.paycore.payment.service;

import com.paycore.payment.client.MerchantClient;
import com.paycore.payment.domain.Payment;
import com.paycore.payment.domain.PaymentStatus;
import com.paycore.payment.dto.MerchantValidationResponse;
import com.paycore.payment.dto.PaymentInitiateRequest;
import com.paycore.payment.dto.PaymentResponse;
import com.paycore.payment.exception.DuplicateOrderException;
import com.paycore.payment.exception.InvalidMerchantApiKeyException;
import com.paycore.payment.exception.PaymentNotFoundException;
import com.paycore.payment.provider.PaymentProviderClient;
import com.paycore.payment.provider.PaymentProviderFactory;
import com.paycore.payment.provider.ProviderPaymentRequest;
import com.paycore.payment.provider.ProviderPaymentResponse;
import com.paycore.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.paycore.payment.event.PaymentEventProducer;
import com.paycore.payment.client.FraudClient;
import com.paycore.payment.dto.FraudCheckRequest;
import com.paycore.payment.dto.FraudCheckResponse;
import com.paycore.payment.notification.PaymentNotificationProducer;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final MerchantClient merchantClient;
    private final PaymentProviderFactory paymentProviderFactory;
    private final PaymentEventProducer paymentEventProducer;
    private final FraudClient fraudClient;
    private final PaymentNotificationProducer paymentNotificationProducer;

    @Transactional
    public PaymentResponse initiatePayment(String apiKey, String ipAddress, PaymentInitiateRequest request) {
        MerchantValidationResponse merchant = merchantClient.validateApiKey(apiKey);

        if (merchant == null || !merchant.valid() || merchant.merchantId() == null) {
            throw new InvalidMerchantApiKeyException();
        }

        if (paymentRepository.existsByMerchantIdAndOrderId(merchant.merchantId(), request.orderId())) {
            throw new DuplicateOrderException(merchant.merchantId(), request.orderId());
        }

        Payment initiatedPayment = Payment.builder()
                .merchantId(merchant.merchantId())
                .amount(request.amount())
                .currency(request.currency().toUpperCase())
                .orderId(request.orderId())
                .providerType(request.providerType())
                .status(PaymentStatus.INITIATED)
                .cardLastFourDigits(extractCardLastFourDigits(request.cardToken()))
                .build();

        Payment savedInitiatedPayment = paymentRepository.save(initiatedPayment);

        paymentEventProducer.publishPaymentEvent(savedInitiatedPayment);

        FraudCheckResponse fraudCheckResponse = fraudClient.checkPaymentRisk(
                new FraudCheckRequest(
                        savedInitiatedPayment.getId(),
                        savedInitiatedPayment.getMerchantId(),
                        savedInitiatedPayment.getAmount(),
                        savedInitiatedPayment.getCurrency(),
                        savedInitiatedPayment.getOrderId(),
                        request.cardToken(),
                        ipAddress
                )
        );

        if (fraudCheckResponse != null && "REJECTED".equals(fraudCheckResponse.decision())) {
            savedInitiatedPayment.setProviderReferenceId(null);
            savedInitiatedPayment.setProviderResponseCode("FRAUD_REJECTED");
            savedInitiatedPayment.setProviderResponseMessage(fraudCheckResponse.message());
            savedInitiatedPayment.setStatus(PaymentStatus.FAILED);

            Payment rejectedPayment = paymentRepository.save(savedInitiatedPayment);
            paymentEventProducer.publishPaymentEvent(rejectedPayment);
            paymentNotificationProducer.publishPaymentNotification(rejectedPayment, merchant.merchantName());

            return toResponse(rejectedPayment);
        }

        PaymentProviderClient providerClient = paymentProviderFactory.getClient(request.providerType());

        ProviderPaymentResponse providerResponse = providerClient.authorize(
                new ProviderPaymentRequest(
                        merchant.merchantId(),
                        request.amount(),
                        request.currency().toUpperCase(),
                        request.orderId(),
                        request.cardToken()
                )
        );

        savedInitiatedPayment.setProviderReferenceId(providerResponse.providerReferenceId());
        savedInitiatedPayment.setProviderResponseCode(providerResponse.responseCode());
        savedInitiatedPayment.setProviderResponseMessage(providerResponse.responseMessage());
        savedInitiatedPayment.setStatus(
                providerResponse.approved() ? PaymentStatus.AUTHORIZED : PaymentStatus.FAILED
        );

        Payment finalPayment = paymentRepository.save(savedInitiatedPayment);

        paymentEventProducer.publishPaymentEvent(finalPayment);
        paymentNotificationProducer.publishPaymentNotification(finalPayment, merchant.merchantName());

        return toResponse(finalPayment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByMerchantId(UUID merchantId) {
        return paymentRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private String extractCardLastFourDigits(String cardToken) {
        if (cardToken == null || cardToken.length() < 4) {
            return null;
        }

        return cardToken.substring(cardToken.length() - 4);
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getMerchantId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getOrderId(),
                payment.getStatus(),
                payment.getProviderType(),
                payment.getProviderReferenceId(),
                payment.getProviderResponseCode(),
                payment.getProviderResponseMessage(),
                payment.getCardLastFourDigits(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}