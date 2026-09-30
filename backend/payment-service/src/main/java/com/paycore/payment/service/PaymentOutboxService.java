package com.paycore.payment.service;

import com.paycore.payment.domain.*;
import com.paycore.payment.dto.PaymentNotificationMessage;
import com.paycore.payment.event.PaymentEvent;
import com.paycore.payment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class PaymentOutboxService {

    private static final String PAYMENT_NOTIFICATION_EVENT_TYPE = "PAYMENT_NOTIFICATION";

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;

    @Value("${paycore.kafka.topics.payment-events}")
    private String paymentEventsTopic;

    @Value("${paycore.rabbitmq.exchange}")
    private String notificationExchange;

    @Value("${paycore.rabbitmq.payment-notification-routing-key}")
    private String paymentNotificationRoutingKey;

    public OutboxEvent enqueuePaymentEvent(Payment payment) {
        validatePersistedPayment(payment);

        UUID eventId = UUID.randomUUID();
        LocalDateTime occurredAt = LocalDateTime.now();
        String eventType = "PAYMENT_" + payment.getStatus().name();

        PaymentEvent event = new PaymentEvent(
                eventId,
                eventType,
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
                occurredAt
        );

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .id(eventId)
                .aggregateType(OutboxAggregateType.PAYMENT)
                .aggregateId(payment.getId())
                .eventType(eventType)
                .destinationType(OutboxDestinationType.KAFKA)
                .destination(paymentEventsTopic)
                .messageKey(payment.getId().toString())
                .routingKey(null)
                .payload(serialize(event, "Payment event could not be serialized"))
                .status(OutboxStatus.PENDING)
                .createdAt(occurredAt)
                .build();

        return outboxEventRepository.save(outboxEvent);
    }

    public OutboxEvent enqueuePaymentNotification(Payment payment, String merchantName) {
        validatePersistedPayment(payment);

        UUID notificationId = UUID.randomUUID();
        LocalDateTime occurredAt = LocalDateTime.now();

        PaymentNotificationMessage message = new PaymentNotificationMessage(
                notificationId,
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
                occurredAt
        );

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .id(notificationId)
                .aggregateType(OutboxAggregateType.PAYMENT)
                .aggregateId(payment.getId())
                .eventType(PAYMENT_NOTIFICATION_EVENT_TYPE)
                .destinationType(OutboxDestinationType.RABBITMQ)
                .destination(notificationExchange)
                .messageKey(null)
                .routingKey(paymentNotificationRoutingKey)
                .payload(serialize(message, "Payment notification could not be serialized"))
                .status(OutboxStatus.PENDING)
                .createdAt(occurredAt)
                .build();

        return outboxEventRepository.save(outboxEvent);
    }

    private String serialize(Object payload, String errorMessage) {
        try {
            return jsonMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException(errorMessage, exception);
        }
    }

    private void validatePersistedPayment(Payment payment) {
        if (payment == null || payment.getId() == null) {
            throw new IllegalArgumentException("Payment must be persisted before creating an outbox event");
        }
    }
}
