package com.paycore.payment.service;

import com.paycore.payment.domain.Payment;
import com.paycore.payment.domain.PaymentStatus;
import com.paycore.payment.exception.DuplicateOrderException;
import com.paycore.payment.exception.PaymentNotFoundException;
import com.paycore.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentPersistenceService {

    private final PaymentRepository paymentRepository;
    private final PaymentOutboxService paymentOutboxService;

    @Transactional
    public Payment createInitiatedPayment(Payment payment) {
        try {
            Payment savedPayment = paymentRepository.saveAndFlush(payment);
            paymentOutboxService.enqueuePaymentEvent(savedPayment);
            return savedPayment;
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateOrderException(payment.getMerchantId(), payment.getOrderId());
        }
    }

    @Transactional
    public Payment finalizePayment(
            UUID paymentId,
            PaymentStatus status,
            String providerReferenceId,
            String providerResponseCode,
            String providerResponseMessage,
            String merchantName
    ) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));

        payment.setProviderReferenceId(providerReferenceId);
        payment.setProviderResponseCode(providerResponseCode);
        payment.setProviderResponseMessage(providerResponseMessage);
        payment.setStatus(status);

        Payment savedPayment = paymentRepository.saveAndFlush(payment);

        paymentOutboxService.enqueuePaymentEvent(savedPayment);
        paymentOutboxService.enqueuePaymentNotification(savedPayment, merchantName);

        return savedPayment;
    }
}
