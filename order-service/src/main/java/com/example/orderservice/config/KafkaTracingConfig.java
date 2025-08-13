package com.example.orderservice.config;

import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration for Kafka tracing integration.
 * Ensures trace propagation through Kafka messages via headers.
 */
@Configuration
public class KafkaTracingConfig {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String SPAN_ID_HEADER = "X-Span-Id";
    private static final String ORDER_ID_HEADER = "X-Order-Id";

    @Autowired
    private Optional<Tracer> tracer;

    /**
     * Producer interceptor to add tracing headers to outgoing messages
     */
    public static class TracingProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {

        private Tracer tracer;

        @Override
        public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
            // Add current trace context to headers
            if (tracer != null && tracer.currentTraceContext().context() != null) {
                var traceContext = tracer.currentTraceContext().context();

                Headers headers = record.headers();
                headers.add(TRACE_ID_HEADER, traceContext.traceId().getBytes(StandardCharsets.UTF_8));
                headers.add(SPAN_ID_HEADER, traceContext.spanId().getBytes(StandardCharsets.UTF_8));
            }

            // Add orderId from MDC if present
            String orderId = MDC.get("orderId");
            if (orderId != null) {
                record.headers().add(ORDER_ID_HEADER, orderId.getBytes(StandardCharsets.UTF_8));
            }

            return record;
        }

        @Override
        public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
            // No-op
        }

        @Override
        public void close() {
            // No-op
        }

        @Override
        public void configure(Map<String, ?> configs) {
            // Tracer will be injected by Spring
        }
    }

    /**
     * Consumer interceptor to extract tracing headers from incoming messages
     */
    public static class TracingConsumerInterceptor<K, V> implements ConsumerInterceptor<K, V> {

        @Override
        public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
            for (ConsumerRecord<K, V> record : records) {
                extractTracingHeaders(record.headers());
            }
            return records;
        }

        @Override
        public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
            // No-op
        }

        @Override
        public void close() {
            // No-op
        }

        @Override
        public void configure(Map<String, ?> configs) {
            // No-op
        }

        private void extractTracingHeaders(Headers headers) {
            // Extract traceId and add to MDC
            Header traceIdHeader = headers.lastHeader(TRACE_ID_HEADER);
            if (traceIdHeader != null) {
                String traceId = new String(traceIdHeader.value(), StandardCharsets.UTF_8);
                MDC.put("traceId", traceId);
            }

            // Extract spanId and add to MDC
            Header spanIdHeader = headers.lastHeader(SPAN_ID_HEADER);
            if (spanIdHeader != null) {
                String spanId = new String(spanIdHeader.value(), StandardCharsets.UTF_8);
                MDC.put("spanId", spanId);
            }

            // Extract orderId and add to MDC
            Header orderIdHeader = headers.lastHeader(ORDER_ID_HEADER);
            if (orderIdHeader != null) {
                String orderId = new String(orderIdHeader.value(), StandardCharsets.UTF_8);
                MDC.put("orderId", orderId);
            }
        }
    }

    /**
     * Configure Kafka listener container factory with tracing
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // Add tracing support with proper error handler
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler(
                (consumerRecord, exception) -> {
                    // Ensure MDC is populated for error handling
                    if (consumerRecord != null) {
                        extractTracingHeaders(consumerRecord.headers());
                    }
                },
                new org.springframework.util.backoff.FixedBackOff()
        ));

        return factory;
    }

    /**
     * Utility method to extract tracing headers
     */
    private void extractTracingHeaders(Headers headers) {
        Header traceIdHeader = headers.lastHeader(TRACE_ID_HEADER);
        if (traceIdHeader != null) {
            String traceId = new String(traceIdHeader.value(), StandardCharsets.UTF_8);
            MDC.put("traceId", traceId);
        }

        Header orderIdHeader = headers.lastHeader(ORDER_ID_HEADER);
        if (orderIdHeader != null) {
            String orderId = new String(orderIdHeader.value(), StandardCharsets.UTF_8);
            MDC.put("orderId", orderId);
        }
    }
}
