package com.example.inventoryservice.consumer;

import com.example.events.OrderCreated;
import com.example.inventoryservice.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class InventoryRetryConsumer {

    private static final Logger logger = LoggerFactory.getLogger(InventoryRetryConsumer.class);

    private final InventoryService inventoryService;

    public InventoryRetryConsumer(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @KafkaListener(
        topics = "${app.kafka.topics.inventory-retry}",
        groupId = "${app.kafka.consumer.retry-group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleInventoryRetry(@Payload OrderCreated orderCreated,
                                    @Header(KafkaHeaders.RECEIVED_KEY) String orderId) {
        logger.info("Processing inventory retry for order: {}", orderId);

        try {
            inventoryService.processOrderCreated(orderId, orderCreated);
        } catch (Exception e) {
            logger.error("Error processing inventory retry for order: {}", orderId, e);
            throw e;
        }
    }
}
