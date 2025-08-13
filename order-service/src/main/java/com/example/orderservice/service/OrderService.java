package com.example.orderservice.service;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderItemDto;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderStatus;
import com.example.orderservice.entity.OutboxEvent;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final OrderEventService orderEventService;

    @Autowired
    public OrderService(OrderRepository orderRepository,
                       OutboxEventRepository outboxEventRepository,
                       ObjectMapper objectMapper,
                       OrderEventService orderEventService) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.orderEventService = orderEventService;
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        logger.info("Creating order for user: {}", request.getUserId());

        try {
            // Generate unique order ID
            String orderId = generateOrderId();

            // Calculate total
            BigDecimal total = calculateTotal(request.getItems());

            // Convert items to JSON
            String itemsJson = objectMapper.writeValueAsString(request.getItems());

            // Create order entity
            Order order = new Order(orderId, request.getUserId(), total, itemsJson, OrderStatus.NEW);
            order = orderRepository.save(order);

            // Create outbox event for OrderCreated
            orderEventService.createOrderCreatedEvent(order, request.getItems());

            logger.info("Order created successfully: {}", orderId);
            return mapToOrderResponse(order, request.getItems());

        } catch (JsonProcessingException e) {
            logger.error("Error serializing order items", e);
            throw new RuntimeException("Failed to create order", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<OrderResponse> getOrder(String orderId) {
        return orderRepository.findByOrderId(orderId)
                .map(order -> {
                    List<OrderItemDto> items = deserializeItems(order.getItems());
                    return mapToOrderResponse(order, items);
                });
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUser(String userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(order -> {
                    List<OrderItemDto> items = deserializeItems(order.getItems());
                    return mapToOrderResponse(order, items);
                })
                .toList();
    }

    public void updateOrderStatus(String orderId, OrderStatus status) {
        logger.info("Updating order {} status to {}", orderId, status);

        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(status);
        orderRepository.save(order);

        // Create appropriate outbox events based on status change
        orderEventService.createStatusChangeEvent(order, previousStatus, status);

        logger.info("Order {} status updated from {} to {}", orderId, previousStatus, status);
    }

    private String generateOrderId() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private BigDecimal calculateTotal(List<OrderItemDto> items) {
        return items.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<OrderItemDto> deserializeItems(String itemsJson) {
        try {
            return objectMapper.readValue(itemsJson, new TypeReference<List<OrderItemDto>>() {});
        } catch (JsonProcessingException e) {
            logger.error("Error deserializing order items", e);
            throw new RuntimeException("Failed to deserialize order items", e);
        }
    }

    private OrderResponse mapToOrderResponse(Order order, List<OrderItemDto> items) {
        return new OrderResponse(
                order.getOrderId(),
                order.getUserId(),
                order.getTotal(),
                items,
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
