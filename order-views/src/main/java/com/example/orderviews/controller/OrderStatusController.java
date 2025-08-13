package com.example.orderviews.controller;

import com.example.orderviews.model.OrderStatus;
import com.example.orderviews.service.OrderStatusQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderStatusController {

    private final OrderStatusQueryService orderStatusQueryService;

    @Autowired
    public OrderStatusController(OrderStatusQueryService orderStatusQueryService) {
        this.orderStatusQueryService = orderStatusQueryService;
    }

    @GetMapping("/{orderId}/status")
    public ResponseEntity<OrderStatus> getOrderStatus(@PathVariable String orderId) {
        OrderStatus orderStatus = orderStatusQueryService.getOrderStatus(orderId);

        if (orderStatus == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(orderStatus);
    }
}
