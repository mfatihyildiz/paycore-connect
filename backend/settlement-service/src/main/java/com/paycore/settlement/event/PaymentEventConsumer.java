package com.paycore.settlement.event;

import com.paycore.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final SettlementService settlementService;

    @KafkaListener(
            topics = "${paycore.kafka.topics.payment-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumePaymentEvent(PaymentEvent event) {
        settlementService.processPaymentEvent(event);
    }
}