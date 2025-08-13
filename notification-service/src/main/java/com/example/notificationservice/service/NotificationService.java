package com.example.notificationservice.service;

import com.example.notificationservice.entity.ProcessedEvent;
import com.example.notificationservice.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final ProcessedEventRepository processedEventRepository;

    public NotificationService(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public void sendOrderCompletedNotification(String orderId, String completedAt, String eventId) {
        if (isEventAlreadyProcessed(eventId)) {
            logger.info("Event {} already processed, skipping notification for order {}", eventId, orderId);
            return;
        }

        // Mock email sending - just log the email content
        String emailContent = String.format("""
            Dear Customer,
            
            Great news! Your order #%s has been completed successfully.
            
            Completion Date: %s
            
            Thank you for choosing our service!
            
            Best regards,
            The E-commerce Team
            """, orderId, completedAt);

        logger.info("Sending order completion email for order {}: {}", orderId, emailContent);

        // Mark event as processed
        processedEventRepository.save(new ProcessedEvent(eventId, "ORDER_COMPLETED"));

        logger.info("Order completion notification sent successfully for order {}", orderId);
    }

    @Transactional
    public void sendOrderCancelledNotification(String orderId, String reason, String cancelledAt, String eventId) {
        if (isEventAlreadyProcessed(eventId)) {
            logger.info("Event {} already processed, skipping notification for order {}", eventId, orderId);
            return;
        }

        // Mock email sending - just log the email content
        String emailContent = String.format("""
            Dear Customer,
            
            We regret to inform you that your order #%s has been cancelled.
            
            Reason: %s
            Cancellation Date: %s
            
            If you have any questions, please don't hesitate to contact our customer service.
            
            Best regards,
            The E-commerce Team
            """, orderId, reason, cancelledAt);

        logger.info("Sending order cancellation email for order {}: {}", orderId, emailContent);

        // Mark event as processed
        processedEventRepository.save(new ProcessedEvent(eventId, "ORDER_CANCELLED"));

        logger.info("Order cancellation notification sent successfully for order {}", orderId);
    }

    private boolean isEventAlreadyProcessed(String eventId) {
        return processedEventRepository.existsByEventId(eventId);
    }
}
