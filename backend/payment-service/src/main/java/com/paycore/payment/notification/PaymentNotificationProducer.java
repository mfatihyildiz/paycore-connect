package com.paycore.payment.notification;

import com.paycore.payment.domain.Payment;
import com.paycore.payment.dto.PaymentNotificationMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentNotificationProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${paycore.rabbitmq.exchange}")
    private String exchange;

    @Value("${paycore.rabbitmq.payment-notification-routing-key}")
    private String paymentNotificationRoutingKey;

    public void publishPaymentNotification(Payment payment, String merchantName) {
        PaymentNotificationMessage message = new PaymentNotificationMessage(
                UUID.randomUUID(),
                payment.getId(),
                payment.getMerchantId(),
                merchantName,
                payment.getAmount(),
                payment.getCurrency(),
                payment.getOrderId(),
                payment.getStatus().name(),
                payment.getProviderType().name(),
                payment.getProviderReferenceId(),
                payment.getProviderResponseCode(),
                payment.getProviderResponseMessage(),
                LocalDateTime.now()
        );

        rabbitTemplate.convertAndSend(exchange, paymentNotificationRoutingKey, message);
    }
}