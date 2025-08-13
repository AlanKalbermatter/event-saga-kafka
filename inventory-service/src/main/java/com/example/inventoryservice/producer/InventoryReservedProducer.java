package com.example.inventoryservice.producer;

import com.example.events.InventoryReserved;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class InventoryReservedProducer {

    private static final Logger logger = LoggerFactory.getLogger(InventoryReservedProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topicName;

    public InventoryReservedProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.inventory-reserved}") String topicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    public void publishInventoryReserved(String orderId, InventoryReserved inventoryReserved) {
        logger.info("Publishing inventory reserved event for order: {}", orderId);

        CompletableFuture<SendResult<String, Object>> future =
            kafkaTemplate.send(topicName, orderId, inventoryReserved);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                logger.info("Successfully published inventory reserved event for order: {} with offset: {}",
                        orderId, result.getRecordMetadata().offset());
            } else {
                logger.error("Failed to publish inventory reserved event for order: {}", orderId, ex);
            }
        });
    }
}
