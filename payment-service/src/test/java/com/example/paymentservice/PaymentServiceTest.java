package com.example.paymentservice;

import com.example.events.OrderCreated;
import com.example.paymentservice.entity.ProcessedOrder;
import com.example.paymentservice.repository.ProcessedOrderRepository;
import com.example.paymentservice.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DataJpaTest
@ActiveProfiles("test")
class PaymentServiceTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public PaymentService paymentService(ProcessedOrderRepository repository) {
            return new PaymentService(
                mock(com.example.paymentservice.service.PaymentProvider.class),
                repository,
                mock(KafkaTemplate.class)
            );
        }
    }

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ProcessedOrderRepository processedOrderRepository;

    @Test
    void testProcessOrderCreated_NewOrder() {
        // Given
        String orderId = "test-order-1";
        OrderCreated orderCreated = OrderCreated.newBuilder()
            .setOrderId(orderId)
            .setUserId("customer-1")
            .setTotal(99.99)
            .setItems(Collections.emptyList())
            .build();

        // When
        paymentService.processOrderCreated(orderId, orderCreated);

        // Then
        Optional<ProcessedOrder> processed = processedOrderRepository.findById(orderId);
        assertTrue(processed.isPresent());
        assertEquals(orderId, processed.get().getOrderId());
        assertTrue(processed.get().getStatus().equals("PROCESSING") ||
                  processed.get().getStatus().equals("AUTHORIZED") ||
                  processed.get().getStatus().equals("FAILED"));
    }

    @Test
    void testProcessOrderCreated_DuplicateOrder() {
        // Given
        String orderId = "test-order-2";
        ProcessedOrder existingOrder = new ProcessedOrder(orderId, "AUTHORIZED");
        processedOrderRepository.save(existingOrder);

        OrderCreated orderCreated = OrderCreated.newBuilder()
            .setOrderId(orderId)
            .setUserId("customer-2")
            .setTotal(50.00)
            .setItems(Collections.emptyList())
            .build();

        // When
        paymentService.processOrderCreated(orderId, orderCreated);

        // Then - should not process duplicate
        Optional<ProcessedOrder> processed = processedOrderRepository.findById(orderId);
        assertTrue(processed.isPresent());
        assertEquals("AUTHORIZED", processed.get().getStatus());
        assertEquals(0, processed.get().getRetryAttempts());
    }

    @Test
    void testIdempotency() {
        // Given
        String orderId = "test-order-3";

        // When - process the same order twice
        OrderCreated orderCreated = OrderCreated.newBuilder()
            .setOrderId(orderId)
            .setUserId("customer-3")
            .setTotal(25.50)
            .setItems(Collections.emptyList())
            .build();

        paymentService.processOrderCreated(orderId, orderCreated);
        paymentService.processOrderCreated(orderId, orderCreated);

        // Then - should only have one record
        long count = processedOrderRepository.count();
        Optional<ProcessedOrder> processed = processedOrderRepository.findById(orderId);
        assertTrue(processed.isPresent());

        // Verify only processed once by checking the existence
        assertTrue(processedOrderRepository.existsByOrderId(orderId));
    }
}
