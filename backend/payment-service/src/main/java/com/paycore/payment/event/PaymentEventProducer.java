package com.paycore.payment.event;

import com.paycore.payment.domain.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    @Value("${paycore.kafka.topics.payment-events}")
    private String paymentEventsTopic;

    public void publishPaymentEvent(Payment payment) {
        PaymentEvent event = new PaymentEvent(
                UUID.randomUUID(),
                "PAYMENT_" + payment.getStatus().name(),
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
                LocalDateTime.now()
        );

        kafkaTemplate.send(paymentEventsTopic, payment.getId().toString(), event);
    }
}