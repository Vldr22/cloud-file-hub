package org.resume.s3filemanager.config;

import lombok.RequiredArgsConstructor;
import org.resume.common.properties.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Конфигурация Kafka retry и Dead Letter Topic.
 * <p>
 * N попыток с exponential backoff, после исчерпания сообщение уходит в {@code <topic>.DLT}.
 *
 * @see KafkaProperties
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaRetryConfig {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaProperties kafkaProperties;

    @Bean
    public DefaultErrorHandler errorHandler() {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), createBackOff());
    }

    private ExponentialBackOffWithMaxRetries createBackOff() {
        KafkaProperties.RetryConfig retry = kafkaProperties.getRetry();

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(retry.getMaxAttempts());
        backOff.setInitialInterval(retry.getBackoffDelay());
        backOff.setMultiplier(retry.getMultiplier());
        backOff.setMaxInterval(retry.getMaxInterval());

        return backOff;
    }
}
