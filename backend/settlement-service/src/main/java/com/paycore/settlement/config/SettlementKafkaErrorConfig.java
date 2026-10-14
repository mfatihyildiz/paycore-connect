package com.paycore.settlement.config;

import com.paycore.settlement.event.PaymentEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Configuration
public class SettlementKafkaErrorConfig {

    @Bean
    public NewTopic settlementDeadLetterTopic(
            @Value("${paycore.kafka.topics.settlement-dlt}") String dltTopic) {

        return TopicBuilder.name(dltTopic).partitions(1).replicas(1).build();
    }

    @Bean("settlementDltKafkaTemplate")
    public KafkaTemplate<String, Object> settlementDltKafkaTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {

        Map<String, Object> producerProperties = new HashMap<>();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProperties.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProperties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        JacksonJsonSerializer<Object> jsonSerializer = new JacksonJsonSerializer<Object>().noTypeInfo();

        Map<Class<?>, Serializer<?>> serializers = new LinkedHashMap<>();
        serializers.put(byte[].class, new ByteArraySerializer());
        serializers.put(PaymentEvent.class, jsonSerializer);

        DelegatingByTypeSerializer valueSerializer = new DelegatingByTypeSerializer(serializers, true);

        DefaultKafkaProducerFactory<String, Object> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProperties, new StringSerializer(), valueSerializer);

        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public DeadLetterPublishingRecoverer settlementDeadLetterPublishingRecoverer(
            @Qualifier("settlementDltKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${paycore.kafka.topics.settlement-dlt}") String dltTopic,
            @Value("${paycore.kafka.retry.dlt-publish-timeout-seconds}") long publishTimeoutSeconds) {

        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(kafkaTemplate, (record, exception) -> new TopicPartition(dltTopic, -1));

        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(publishTimeoutSeconds));

        return recoverer;
    }

    @Bean
    public DefaultErrorHandler settlementKafkaErrorHandler(
            DeadLetterPublishingRecoverer settlementDeadLetterPublishingRecoverer,
            @Value("${paycore.kafka.retry.max-retries}") int maxRetries,
            @Value("${paycore.kafka.retry.initial-interval-ms}") long initialIntervalMs,
            @Value("${paycore.kafka.retry.multiplier}") double multiplier,
            @Value("${paycore.kafka.retry.max-interval-ms}") long maxIntervalMs) {

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);

        backOff.setInitialInterval(initialIntervalMs);
        backOff.setMultiplier(multiplier);
        backOff.setMaxInterval(maxIntervalMs);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(settlementDeadLetterPublishingRecoverer, backOff);

        errorHandler.setRetryListeners(
                (record, ex, deliveryAttempt) -> log.warn(
                        "Settlement Kafka delivery failed: topic={}, partition={}, offset={}, key={}, attempt={}, error={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        record.key(),
                        deliveryAttempt,
                        ex.getClass().getSimpleName()
                )
        );

        return errorHandler;
    }
}
