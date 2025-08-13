package com.example.paymentservice.consumer;

import com.example.events.OrderCreated;
import com.example.paymentservice.service.PaymentService;
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

    private final PaymentService paymentService;

    public OrderCreatedConsumer(PaymentService paymentService) {
        this.paymentService = paymentService;
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
            paymentService.processOrderCreated(orderId, orderCreated);
        } catch (Exception e) {
            logger.error("Error processing order created event for order: {}", orderId, e);
            throw e; // Let Kafka handle the retry/DLT based on configuration
        }
    }
}
