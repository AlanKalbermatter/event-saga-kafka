package com.example.notificationservice.listener;

import com.example.events.OrderCompleted;
import com.example.events.OrderCancelled;
import com.example.notificationservice.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger logger = LoggerFactory.getLogger(OrderEventListener.class);

    private final NotificationService notificationService;

    public OrderEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "order.completed", groupId = "notification-service")
    public void handleOrderCompleted(@Payload OrderCompleted orderCompleted,
                                   @Header(KafkaHeaders.RECEIVED_KEY) String key,
                                   @Header(KafkaHeaders.OFFSET) long offset,
                                   @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {

        logger.info("Received OrderCompleted event: orderId={}, completedAt={}, partition={}, offset={}",
                   orderCompleted.getOrderId(), orderCompleted.getCompletedAt(), partition, offset);

        try {
            // Use partition-offset combination as unique event ID for idempotency
            String eventId = String.format("order-completed-%d-%d", partition, offset);

            notificationService.sendOrderCompletedNotification(
                orderCompleted.getOrderId().toString(),
                orderCompleted.getCompletedAt().toString(),
                eventId
            );
        } catch (Exception e) {
            logger.error("Failed to process OrderCompleted event for order {}: {}",
                        orderCompleted.getOrderId(), e.getMessage(), e);
            throw e; // Re-throw to trigger Kafka retry mechanism
        }
    }

    @KafkaListener(topics = "order.cancelled", groupId = "notification-service")
    public void handleOrderCancelled(@Payload OrderCancelled orderCancelled,
                                   @Header(KafkaHeaders.RECEIVED_KEY) String key,
                                   @Header(KafkaHeaders.OFFSET) long offset,
                                   @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {

        logger.info("Received OrderCancelled event: orderId={}, reason={}, cancelledAt={}, partition={}, offset={}",
                   orderCancelled.getOrderId(), orderCancelled.getReason(),
                   orderCancelled.getCancelledAt(), partition, offset);

        try {
            // Use partition-offset combination as unique event ID for idempotency
            String eventId = String.format("order-cancelled-%d-%d", partition, offset);

            notificationService.sendOrderCancelledNotification(
                orderCancelled.getOrderId().toString(),
                orderCancelled.getReason().toString(),
                orderCancelled.getCancelledAt().toString(),
                eventId
            );
        } catch (Exception e) {
            logger.error("Failed to process OrderCancelled event for order {}: {}",
                        orderCancelled.getOrderId(), e.getMessage(), e);
            throw e; // Re-throw to trigger Kafka retry mechanism
        }
    }
}
