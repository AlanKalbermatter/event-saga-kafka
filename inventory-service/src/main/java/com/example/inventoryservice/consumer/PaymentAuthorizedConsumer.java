package com.example.inventoryservice.consumer;

import com.example.events.PaymentAuthorized;
import com.example.inventoryservice.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class PaymentAuthorizedConsumer {

    private static final Logger logger = LoggerFactory.getLogger(PaymentAuthorizedConsumer.class);

    private final InventoryService inventoryService;

    public PaymentAuthorizedConsumer(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @KafkaListener(
        topics = "${app.kafka.topics.payment-authorized}",
        groupId = "${app.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentAuthorized(@Payload PaymentAuthorized paymentAuthorized,
                                       @Header(KafkaHeaders.RECEIVED_KEY) String orderId) {
        logger.info("Received payment authorized event for order: {}", orderId);

        try {
            inventoryService.processPaymentAuthorized(orderId, paymentAuthorized);
        } catch (Exception e) {
            logger.error("Error processing payment authorized event for order: {}", orderId, e);
            throw e; // Let Kafka handle the retry/DLT based on configuration
        }
    }
}
