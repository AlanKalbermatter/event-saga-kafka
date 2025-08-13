package com.example.inventoryservice.producer;

import com.example.events.InventoryRejected;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class InventoryRejectedProducer {

    private static final Logger logger = LoggerFactory.getLogger(InventoryRejectedProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topicName;

    public InventoryRejectedProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.inventory-rejected}") String topicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    public void publishInventoryRejected(String orderId, InventoryRejected inventoryRejected) {
        logger.info("Publishing inventory rejected event for order: {}", orderId);

        CompletableFuture<SendResult<String, Object>> future =
            kafkaTemplate.send(topicName, orderId, inventoryRejected);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                logger.info("Successfully published inventory rejected event for order: {} with offset: {}",
                        orderId, result.getRecordMetadata().offset());
            } else {
                logger.error("Failed to publish inventory rejected event for order: {}", orderId, ex);
            }
        });
    }
}
