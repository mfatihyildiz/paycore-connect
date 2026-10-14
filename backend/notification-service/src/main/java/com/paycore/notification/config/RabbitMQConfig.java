package com.paycore.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableRabbit
@Configuration
public class RabbitMQConfig {

    @Value("${paycore.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${paycore.rabbitmq.payment-notification-queue}")
    private String paymentNotificationQueueName;

    @Value("${paycore.rabbitmq.payment-notification-routing-key}")
    private String paymentNotificationRoutingKey;

    @Value("${paycore.rabbitmq.dead-letter-exchange}")
    private String deadLetterExchangeName;

    @Value("${paycore.rabbitmq.payment-notification-dlq}")
    private String paymentNotificationDlqName;

    @Value("${paycore.rabbitmq.dead-letter-routing-key}")
    private String deadLetterRoutingKey;

    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(exchangeName);
    }

    @Bean
    public DirectExchange notificationDeadLetterExchange() {
        return new DirectExchange(deadLetterExchangeName);
    }

    @Bean
    public Queue paymentNotificationQueue() {
        return QueueBuilder.durable(paymentNotificationQueueName)
                .deadLetterExchange(deadLetterExchangeName)
                .deadLetterRoutingKey(deadLetterRoutingKey)
                .build();
    }

    @Bean
    public Queue paymentNotificationDeadLetterQueue() {
        return QueueBuilder.durable(paymentNotificationDlqName).build();
    }

    @Bean
    public Binding paymentNotificationBinding(
            Queue paymentNotificationQueue,
            DirectExchange notificationExchange) {

        return BindingBuilder
                .bind(paymentNotificationQueue)
                .to(notificationExchange)
                .with(paymentNotificationRoutingKey);
    }

    @Bean
    public Binding paymentNotificationDeadLetterBinding(
            Queue paymentNotificationDeadLetterQueue,
            DirectExchange notificationDeadLetterExchange) {

        return BindingBuilder
                .bind(paymentNotificationDeadLetterQueue)
                .to(notificationDeadLetterExchange)
                .with(deadLetterRoutingKey);
    }

    @Bean
    public JacksonJsonMessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}