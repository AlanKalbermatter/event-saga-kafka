package com.example.orderservice.service;

import com.example.events.OrderCreated;
import com.example.events.OrderItem;
import com.example.orderservice.dto.OrderItemDto;
import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderStatus;
import com.example.orderservice.entity.OutboxEvent;
import com.example.orderservice.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderEventService {

    private static final Logger logger = LoggerFactory.getLogger(OrderEventService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public OrderEventService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public void createOrderCreatedEvent(Order order, List<OrderItemDto> items) {
        logger.info("Creating OrderCreated event for order: {}", order.getOrderId());

        try {
            // Convert DTOs to Avro objects
            List<OrderItem> avroItems = items.stream()
                    .map(dto -> OrderItem.newBuilder()
                            .setSku(dto.getSku())
                            .setQty(dto.getQty())
                            .setPrice(dto.getPrice().doubleValue())
                            .build())
                    .toList();

            // Create Avro event
            OrderCreated orderCreated = OrderCreated.newBuilder()
                    .setOrderId(order.getOrderId())
                    .setUserId(order.getUserId())
                    .setTotal(order.getTotal().doubleValue())
                    .setItems(avroItems)
                    .setCreatedAt(order.getCreatedAt().toString())
                    .setVersion(1)
                    .build();

            // Convert to JSON for storage
            String payload = orderCreated.toString();

            // Create headers
            Map<String, String> headers = createEventHeaders("OrderCreated", order.getOrderId());
            String headersJson = objectMapper.writeValueAsString(headers);

            // Create outbox event
            OutboxEvent outboxEvent = new OutboxEvent(
                    "Order",
                    order.getOrderId(),
                    "OrderCreated",
                    payload,
                    headersJson
            );

            outboxEventRepository.save(outboxEvent);
            logger.info("OrderCreated event saved to outbox for order: {}", order.getOrderId());

        } catch (JsonProcessingException e) {
            logger.error("Error creating OrderCreated event for order: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to create OrderCreated event", e);
        }
    }

    public void createStatusChangeEvent(Order order, OrderStatus previousStatus, OrderStatus newStatus) {
        logger.info("Creating status change event for order: {} from {} to {}",
                order.getOrderId(), previousStatus, newStatus);

        try {
            // Create event payload based on status
            Map<String, Object> payload = new HashMap<>();
            payload.put("orderId", order.getOrderId());
            payload.put("userId", order.getUserId());
            payload.put("previousStatus", previousStatus.toString());
            payload.put("newStatus", newStatus.toString());
            payload.put("updatedAt", order.getUpdatedAt().toString());

            String payloadJson = objectMapper.writeValueAsString(payload);

            // Create headers
            Map<String, String> headers = createEventHeaders("OrderStatusChanged", order.getOrderId());
            String headersJson = objectMapper.writeValueAsString(headers);

            // Create outbox event
            OutboxEvent outboxEvent = new OutboxEvent(
                    "Order",
                    order.getOrderId(),
                    "OrderStatusChanged",
                    payloadJson,
                    headersJson
            );

            outboxEventRepository.save(outboxEvent);
            logger.info("OrderStatusChanged event saved to outbox for order: {}", order.getOrderId());

        } catch (JsonProcessingException e) {
            logger.error("Error creating status change event for order: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to create status change event", e);
        }
    }

    private Map<String, String> createEventHeaders(String eventType, String orderId) {
        Map<String, String> headers = new HashMap<>();
        headers.put("eventType", eventType);
        headers.put("aggregateId", orderId);
        headers.put("aggregateType", "Order");
        headers.put("eventVersion", "1");
        headers.put("timestamp", Instant.now().toString());
        headers.put("source", "order-service");
        return headers;
    }
}
