package com.paycore.ledger.event;

import com.paycore.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final LedgerService ledgerService;

    @KafkaListener(
            topics = "${paycore.kafka.topics.payment-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumePaymentEvent(PaymentEvent event) {
        ledgerService.savePaymentEvent(event);
    }
}