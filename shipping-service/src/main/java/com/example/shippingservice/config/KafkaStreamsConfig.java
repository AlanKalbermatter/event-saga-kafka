package com.example.shippingservice.config;

import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;

import static org.apache.kafka.streams.StreamsConfig.*;

@Configuration
public class KafkaStreamsConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaStreamsConfig.class);

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_BUILDER_BEAN_NAME)
    public StreamsBuilderFactoryBeanConfigurer streamsBuilderFactoryBeanConfigurer() {
        return factoryBean -> {
            // Set uncaught exception handler
            factoryBean.setKafkaStreamsCustomizer(kafkaStreams -> {
                kafkaStreams.setUncaughtExceptionHandler(exception -> {
                    log.error("Kafka Streams uncaught exception occurred. Shutting down application.", exception);
                    return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_APPLICATION;
                });
            });

            // Additional configuration for monitoring and error handling
            factoryBean.getStreamsConfiguration().put(
                DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                LogAndContinueExceptionHandler.class
            );
        };
    }
}
