package com.example.inventoryservice.producer;

import com.example.events.PaymentFailed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class PaymentFailedProducer {

    private static final Logger logger = LoggerFactory.getLogger(PaymentFailedProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topicName;

    public PaymentFailedProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.payment-failed}") String topicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    public void publishPaymentFailed(String orderId, PaymentFailed paymentFailed) {
        logger.info("Publishing payment failed compensation event for order: {}", orderId);

        CompletableFuture<SendResult<String, Object>> future =
            kafkaTemplate.send(topicName, orderId, paymentFailed);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                logger.info("Successfully published payment failed compensation event for order: {} with offset: {}",
                        orderId, result.getRecordMetadata().offset());
            } else {
                logger.error("Failed to publish payment failed compensation event for order: {}", orderId, ex);
            }
        });
    }
}
