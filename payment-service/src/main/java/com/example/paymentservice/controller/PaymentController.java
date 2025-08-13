package com.example.paymentservice.controller;

import com.example.paymentservice.entity.ProcessedOrder;
import com.example.paymentservice.repository.ProcessedOrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final ProcessedOrderRepository processedOrderRepository;

    public PaymentController(ProcessedOrderRepository processedOrderRepository) {
        this.processedOrderRepository = processedOrderRepository;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "payment-service");
        health.put("timestamp", System.currentTimeMillis());

        // Add some basic metrics
        long totalProcessed = processedOrderRepository.count();
        health.put("totalOrdersProcessed", totalProcessed);

        return ResponseEntity.ok(health);
    }

    @GetMapping("/orders/{orderId}/status")
    public ResponseEntity<ProcessedOrder> getOrderStatus(@PathVariable String orderId) {
        return processedOrderRepository.findById(orderId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/orders")
    public ResponseEntity<List<ProcessedOrder>> getProcessedOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Simple pagination without Spring Data's Pageable for brevity
        List<ProcessedOrder> orders = processedOrderRepository.findAll();
        return ResponseEntity.ok(orders);
    }
}
