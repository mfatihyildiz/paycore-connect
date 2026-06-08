package com.paycore.payment.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(exchangeName);
    }

    @Bean
    public Queue paymentNotificationQueue() {
        return QueueBuilder.durable(paymentNotificationQueueName).build();
    }

    @Bean
    public Binding paymentNotificationBinding(
            Queue paymentNotificationQueue,
            DirectExchange notificationExchange
    ) {
        return BindingBuilder
                .bind(paymentNotificationQueue)
                .to(notificationExchange)
                .with(paymentNotificationRoutingKey);
    }

    @Bean
    public JacksonJsonMessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory,
            JacksonJsonMessageConverter rabbitMessageConverter
    ) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(rabbitMessageConverter);
        return rabbitTemplate;
    }
}