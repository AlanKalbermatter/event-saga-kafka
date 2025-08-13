package com.example.orderservice.service;

import com.example.orderservice.entity.OutboxEvent;
import com.example.orderservice.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class OutboxRelay {

    private static final Logger logger = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.topics.order-events}")
    private String orderEventsTopic;

    @Value("${app.outbox.batch-size:100}")
    private int batchSize;

    @Autowired
    public OutboxRelay(OutboxEventRepository outboxEventRepository,
                      KafkaTemplate<String, Object> kafkaTemplate,
                      ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${app.outbox.polling-interval:5000}")
    @Transactional
    public void relayEvents() {
        try {
            List<OutboxEvent> unprocessedEvents = outboxEventRepository.findUnprocessedEventsWithLimit(batchSize);

            if (unprocessedEvents.isEmpty()) {
                return;
            }

            logger.info("Processing {} outbox events", unprocessedEvents.size());

            for (OutboxEvent event : unprocessedEvents) {
                try {
                    publishEvent(event);
                    event.markAsProcessed();
                    outboxEventRepository.save(event);

                    logger.debug("Successfully published and marked event as processed: {}", event.getId());

                } catch (Exception e) {
                    logger.error("Failed to publish event: {}", event.getId(), e);
                    // Continue with next event, will retry on next scheduled run
                }
            }

            logger.info("Completed processing outbox events batch");

        } catch (Exception e) {
            logger.error("Error during outbox relay processing", e);
        }
    }

    private void publishEvent(OutboxEvent event) throws Exception {
        String topic = determineTopicForEvent(event);
        String key = event.getAggregateId();

        // Parse the payload - for OrderCreated events, we need to convert to Avro
        Object payload = parseEventPayload(event);

        // Create producer record
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, payload);

        // Add headers
        if (event.getHeaders() != null) {
            Map<String, String> headers = objectMapper.readValue(
                event.getHeaders(),
                new TypeReference<Map<String, String>>() {}
            );

            headers.forEach((headerKey, headerValue) ->
                record.headers().add(new RecordHeader(headerKey, headerValue.getBytes()))
            );
        }

        // Add outbox metadata headers
        record.headers().add(new RecordHeader("outbox-event-id", event.getId().toString().getBytes()));
        record.headers().add(new RecordHeader("outbox-event-type", event.getEventType().getBytes()));
        record.headers().add(new RecordHeader("outbox-created-at", event.getCreatedAt().toString().getBytes()));

        // Send to Kafka
        kafkaTemplate.send(record).get(); // Synchronous send for reliability

        logger.debug("Published event {} to topic {} with key {}",
            event.getId(), topic, key);
    }

    private String determineTopicForEvent(OutboxEvent event) {
        // Route events to appropriate topics based on event type
        return switch (event.getEventType()) {
            case "OrderCreated", "OrderStatusChanged" -> orderEventsTopic;
            default -> orderEventsTopic; // Default fallback
        };
    }

    private Object parseEventPayload(OutboxEvent event) throws Exception {
        if ("OrderCreated".equals(event.getEventType())) {
            // For OrderCreated events, convert JSON payload to Avro format
            Map<String, Object> jsonPayload = objectMapper.readValue(
                event.getPayload(),
                new TypeReference<Map<String, Object>>() {}
            );

            // Create Avro OrderCreated object manually since we have dependency issues
            // For now, return the JSON payload - can be enhanced later for proper Avro
            return jsonPayload;
        } else {
            // For other events, parse as JSON
            return objectMapper.readValue(event.getPayload(), Object.class);
        }
    }

    // Manual trigger for testing/debugging
    public void processOutboxEventsManually() {
        logger.info("Manually triggering outbox event processing");
        relayEvents();
    }

    // Health check method
    public long getUnprocessedEventCount() {
        return outboxEventRepository.countUnprocessedEvents();
    }

    // Cleanup old processed events (can be scheduled separately)
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    @Transactional
    public void cleanupOldProcessedEvents() {
        try {
            Instant cutoff = Instant.now().minusSeconds(7 * 24 * 60 * 60); // 7 days ago
            List<OutboxEvent> oldEvents = outboxEventRepository.findProcessedEventsBefore(cutoff);

            if (!oldEvents.isEmpty()) {
                outboxEventRepository.deleteAll(oldEvents);
                logger.info("Cleaned up {} old processed outbox events", oldEvents.size());
            }
        } catch (Exception e) {
            logger.error("Error during outbox cleanup", e);
        }
    }
}
