package com.example.paymentservice.service;

import com.example.events.OrderCreated;
import com.example.events.PaymentAuthorized;
import com.example.events.PaymentFailed;
import com.example.events.PaymentRequested;
import com.example.paymentservice.entity.ProcessedOrder;
import com.example.paymentservice.repository.ProcessedOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentProvider paymentProvider;
    private final ProcessedOrderRepository processedOrderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.payment-authorized}")
    private String paymentAuthorizedTopic;

    @Value("${app.kafka.topics.payment-failed}")
    private String paymentFailedTopic;

    @Value("${app.kafka.topics.payment-requested-retry}")
    private String paymentRequestedRetryTopic;

    @Value("${app.kafka.topics.payment-requested-dlt}")
    private String paymentRequestedDltTopic;

    @Value("${app.payment.max-retry-attempts:3}")
    private int maxRetryAttempts;

    public PaymentService(PaymentProvider paymentProvider,
                         ProcessedOrderRepository processedOrderRepository,
                         KafkaTemplate<String, Object> kafkaTemplate) {
        this.paymentProvider = paymentProvider;
        this.processedOrderRepository = processedOrderRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public void processOrderCreated(String orderId, OrderCreated orderCreated) {
        logger.info("Processing order created event for order: {}", orderId);

        // Check idempotency - avoid duplicate processing
        if (processedOrderRepository.existsByOrderId(orderId)) {
            logger.warn("Order {} already processed, skipping", orderId);
            return;
        }

        // Mark as processing
        ProcessedOrder processedOrder = new ProcessedOrder(orderId, "PROCESSING");
        processedOrderRepository.save(processedOrder);

        // Process payment
        processPayment(orderId, orderCreated, 0);
    }

    @Transactional
    public void processPaymentRetry(String orderId, PaymentRequested paymentRequested, int retryAttempt) {
        logger.info("Processing payment retry for order: {}, attempt: {}", orderId, retryAttempt);

        ProcessedOrder processedOrder = processedOrderRepository.findById(orderId)
            .orElse(new ProcessedOrder(orderId, "PROCESSING"));

        processedOrder.setRetryAttempts(retryAttempt);
        processedOrderRepository.save(processedOrder);

        // Convert PaymentRequested to OrderCreated for processing
        OrderCreated orderCreated = OrderCreated.newBuilder()
            .setOrderId(paymentRequested.getOrderId())
            .setUserId(paymentRequested.getPaymentId()) // Using paymentId as userId for now
            .setTotal(paymentRequested.getAmount())
            .setItems(java.util.Collections.emptyList())
            .build();

        processPayment(orderId, orderCreated, retryAttempt);
    }

    private void processPayment(String orderId, OrderCreated orderCreated, int retryAttempt) {
        try {
            PaymentProvider.PaymentResult result = paymentProvider.authorizePayment(
                orderId,
                BigDecimal.valueOf(orderCreated.getTotal()),
                orderCreated.getUserId()
            );

            if (result.isSuccess()) {
                handlePaymentSuccess(orderId, orderCreated, result.getTransactionId());
            } else {
                handlePaymentFailure(orderId, orderCreated, result.getErrorCode(),
                                   result.getErrorMessage(), retryAttempt);
            }
        } catch (Exception e) {
            logger.error("Error processing payment for order: {}", orderId, e);
            handlePaymentFailure(orderId, orderCreated, "PROCESSING_ERROR",
                               e.getMessage(), retryAttempt);
        }
    }

    private void handlePaymentSuccess(String orderId, OrderCreated orderCreated, String transactionId) {
        logger.info("Payment authorized successfully for order: {}, transactionId: {}", orderId, transactionId);

        // Update status
        ProcessedOrder processedOrder = processedOrderRepository.findById(orderId).orElseThrow();
        processedOrder.setStatus("AUTHORIZED");
        processedOrderRepository.save(processedOrder);

        // Send payment authorized event
        PaymentAuthorized paymentAuthorized = PaymentAuthorized.newBuilder()
            .setOrderId(orderId)
            .setPaymentId(transactionId)
            .setAmount(orderCreated.getTotal())
            .setAuthorizedAt(java.time.Instant.now().toString())
            .build();

        kafkaTemplate.send(paymentAuthorizedTopic, orderId, paymentAuthorized);
        logger.info("Payment authorized event sent for order: {}", orderId);
    }

    private void handlePaymentFailure(String orderId, OrderCreated orderCreated,
                                    String errorCode, String errorMessage, int retryAttempt) {
        logger.warn("Payment failed for order: {}, attempt: {}, error: {}", orderId, retryAttempt, errorMessage);

        ProcessedOrder processedOrder = processedOrderRepository.findById(orderId).orElseThrow();

        if (retryAttempt < maxRetryAttempts) {
            // Schedule retry with exponential backoff
            scheduleRetry(orderId, orderCreated, retryAttempt + 1);
        } else {
            // Max attempts exceeded - mark as failed and send to DLT
            processedOrder.setStatus("FAILED");
            processedOrderRepository.save(processedOrder);

            sendToDeadLetterTopic(orderId, orderCreated, errorCode, errorMessage);
            sendPaymentFailedEvent(orderId, orderCreated, errorCode, errorMessage);
        }
    }

    private void scheduleRetry(String orderId, OrderCreated orderCreated, int nextAttempt) {
        logger.info("Scheduling retry for order: {}, attempt: {}", orderId, nextAttempt);

        // Calculate exponential backoff delay (base 2 seconds, max 60 seconds)
        long delayMs = Math.min(2000L * (1L << (nextAttempt - 1)), 60000L);

        PaymentRequested paymentRequested = PaymentRequested.newBuilder()
            .setOrderId(orderId)
            .setPaymentId("RETRY-" + orderId + "-" + nextAttempt)
            .setAmount(orderCreated.getTotal())
            .setProvider("dummy-provider")
            .setRequestedAt(java.time.Instant.now().toString())
            .build();

        Message<PaymentRequested> message = MessageBuilder
            .withPayload(paymentRequested)
            .setHeader(KafkaHeaders.TOPIC, paymentRequestedRetryTopic)
            .setHeader(KafkaHeaders.KEY, orderId)
            .setHeader("x-retry-attempt", nextAttempt)
            .setHeader("x-delay-ms", delayMs)
            .setHeader("x-scheduled-time", System.currentTimeMillis() + delayMs)
            .build();

        kafkaTemplate.send(message);
        logger.info("Retry scheduled for order: {}, attempt: {}, delay: {}ms", orderId, nextAttempt, delayMs);
    }

    private void sendToDeadLetterTopic(String orderId, OrderCreated orderCreated, String errorCode, String errorMessage) {
        logger.error("Sending order {} to DLT after max retry attempts", orderId);

        PaymentRequested paymentRequested = PaymentRequested.newBuilder()
            .setOrderId(orderId)
            .setPaymentId("DLT-" + orderId)
            .setAmount(orderCreated.getTotal())
            .setProvider("dummy-provider")
            .setRequestedAt(java.time.Instant.now().toString())
            .build();

        Message<PaymentRequested> message = MessageBuilder
            .withPayload(paymentRequested)
            .setHeader(KafkaHeaders.TOPIC, paymentRequestedDltTopic)
            .setHeader(KafkaHeaders.KEY, orderId)
            .setHeader("x-error-code", errorCode)
            .setHeader("x-error-message", errorMessage)
            .setHeader("x-max-attempts-exceeded", true)
            .build();

        kafkaTemplate.send(message);
    }

    private void sendPaymentFailedEvent(String orderId, OrderCreated orderCreated, String errorCode, String errorMessage) {
        PaymentFailed paymentFailed = PaymentFailed.newBuilder()
            .setOrderId(orderId)
            .setPaymentId("FAILED-" + orderId)
            .setReason(errorCode + ": " + errorMessage)
            .setFailedAt(java.time.Instant.now().toString())
            .build();

        kafkaTemplate.send(paymentFailedTopic, orderId, paymentFailed);
        logger.info("Payment failed event sent for order: {}", orderId);
    }
}
