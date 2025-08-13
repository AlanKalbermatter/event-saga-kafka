package com.example.paymentservice.consumer;

import com.example.events.PaymentRequested;
import com.example.paymentservice.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

@Component
public class PaymentRetryConsumer {

    private static final Logger logger = LoggerFactory.getLogger(PaymentRetryConsumer.class);

    private final PaymentService paymentService;
    private final DelayQueue<DelayedRetryMessage> delayQueue = new DelayQueue<>();

    public PaymentRetryConsumer(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @KafkaListener(
        topics = "${app.kafka.topics.payment-requested-retry}",
        groupId = "${app.kafka.consumer.retry-group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentRetry(@Payload PaymentRequested paymentRequested,
                                  @Header(KafkaHeaders.RECEIVED_KEY) String orderId,
                                  @Header("x-retry-attempt") Integer retryAttempt,
                                  @Header("x-delay-ms") Long delayMs,
                                  @Header("x-scheduled-time") Long scheduledTime) {

        logger.info("Received payment retry request for order: {}, attempt: {}, delay: {}ms",
                   orderId, retryAttempt, delayMs);

        // Add to delay queue for scheduled processing
        DelayedRetryMessage delayedMessage = new DelayedRetryMessage(
            orderId, paymentRequested, retryAttempt, scheduledTime
        );

        delayQueue.offer(delayedMessage);
        logger.debug("Added retry message to delay queue for order: {}, scheduled for: {}",
                    orderId, scheduledTime);
    }

    @Scheduled(fixedDelay = 1000) // Check every second
    public void processDelayedRetries() {
        DelayedRetryMessage message;
        while ((message = delayQueue.poll()) != null) {
            try {
                logger.info("Processing delayed retry for order: {}, attempt: {}",
                           message.getOrderId(), message.getRetryAttempt());

                paymentService.processPaymentRetry(
                    message.getOrderId(),
                    message.getPaymentRequested(),
                    message.getRetryAttempt()
                );

            } catch (Exception e) {
                logger.error("Error processing delayed retry for order: {}",
                           message.getOrderId(), e);
                // Could implement additional error handling here
            }
        }
    }

    private static class DelayedRetryMessage implements Delayed {
        private final String orderId;
        private final PaymentRequested paymentRequested;
        private final Integer retryAttempt;
        private final long scheduledTime;

        public DelayedRetryMessage(String orderId, PaymentRequested paymentRequested,
                                 Integer retryAttempt, long scheduledTime) {
            this.orderId = orderId;
            this.paymentRequested = paymentRequested;
            this.retryAttempt = retryAttempt;
            this.scheduledTime = scheduledTime;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long delayMs = scheduledTime - System.currentTimeMillis();
            return unit.convert(delayMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            if (this.scheduledTime < ((DelayedRetryMessage) other).scheduledTime) {
                return -1;
            }
            if (this.scheduledTime > ((DelayedRetryMessage) other).scheduledTime) {
                return 1;
            }
            return 0;
        }

        public String getOrderId() {
            return orderId;
        }

        public PaymentRequested getPaymentRequested() {
            return paymentRequested;
        }

        public Integer getRetryAttempt() {
            return retryAttempt;
        }
    }
}
