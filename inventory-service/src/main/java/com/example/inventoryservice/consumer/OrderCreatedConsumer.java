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
public class OrderCreatedConsumer {

    private static final Logger logger = LoggerFactory.getLogger(OrderCreatedConsumer.class);

    private final InventoryService inventoryService;

    public OrderCreatedConsumer(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @KafkaListener(
        topics = "${app.kafka.topics.order-created}",
        groupId = "${app.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCreated(@Payload OrderCreated orderCreated,
                                  @Header(KafkaHeaders.RECEIVED_KEY) String orderId) {
        logger.info("Received order created event for order: {}", orderId);

        try {
            inventoryService.processOrderCreated(orderId, orderCreated);
        } catch (Exception e) {
            logger.error("Error processing order created event for order: {}", orderId, e);
            throw e; // Let Kafka handle the retry/DLT based on configuration
        }
    }
}
