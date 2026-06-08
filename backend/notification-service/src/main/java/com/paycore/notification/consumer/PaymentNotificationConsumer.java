package com.paycore.notification.consumer;

import com.paycore.notification.dto.PaymentNotificationMessage;
import com.paycore.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentNotificationConsumer {

    private final NotificationService notificationService;

    @RabbitListener(queues = "${paycore.rabbitmq.payment-notification-queue}")
    public void consumePaymentNotification(PaymentNotificationMessage message) {
        notificationService.processPaymentNotification(message);
    }
}