package com.example.paymentservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Random;

@Service
public class PaymentProvider {

    private static final Logger logger = LoggerFactory.getLogger(PaymentProvider.class);
    private final Random random = new Random();

    public PaymentResult authorizePayment(String orderId, BigDecimal amount, String customerId) {
        logger.info("Attempting payment authorization for order: {}, amount: {}, customer: {}",
                   orderId, amount, customerId);

        // Simulate processing delay
        try {
            Thread.sleep(100 + random.nextInt(200)); // 100-300ms delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 80% success rate, 20% failure rate
        boolean isSuccess = random.nextDouble() < 0.8;

        if (isSuccess) {
            String transactionId = "TXN-" + System.currentTimeMillis() + "-" + random.nextInt(1000);
            logger.info("Payment authorization successful for order: {}, transactionId: {}", orderId, transactionId);
            return PaymentResult.success(transactionId);
        } else {
            String errorCode = "INSUFFICIENT_FUNDS";
            String errorMessage = "Payment authorization failed - insufficient funds";
            logger.warn("Payment authorization failed for order: {}, error: {}", orderId, errorMessage);
            return PaymentResult.failure(errorCode, errorMessage);
        }
    }

    public static class PaymentResult {
        private final boolean success;
        private final String transactionId;
        private final String errorCode;
        private final String errorMessage;

        private PaymentResult(boolean success, String transactionId, String errorCode, String errorMessage) {
            this.success = success;
            this.transactionId = transactionId;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public static PaymentResult success(String transactionId) {
            return new PaymentResult(true, transactionId, null, null);
        }

        public static PaymentResult failure(String errorCode, String errorMessage) {
            return new PaymentResult(false, null, errorCode, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
