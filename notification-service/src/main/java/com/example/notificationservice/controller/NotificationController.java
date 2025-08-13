package com.example.notificationservice.controller;

import com.example.notificationservice.dto.EventTypeInfo;
import com.example.notificationservice.dto.NotificationStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notification Service", description = "Service for handling order notification events")
public class NotificationController {

    @GetMapping("/event-types")
    @Operation(
        summary = "Get supported event types",
        description = "Returns a list of all event types that this notification service can handle"
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved event types")
    public List<EventTypeInfo> getSupportedEventTypes() {
        return List.of(
            new EventTypeInfo(
                "order.completed",
                "OrderCompleted",
                "Triggered when an order is successfully completed. Sends a completion confirmation email to the customer.",
                "com.example.events.OrderCompleted"
            ),
            new EventTypeInfo(
                "order.cancelled",
                "OrderCancelled",
                "Triggered when an order is cancelled. Sends a cancellation notification email to the customer.",
                "com.example.events.OrderCancelled"
            )
        );
    }

    @GetMapping("/status")
    @Operation(
        summary = "Get notification service status",
        description = "Returns the current status and health of the notification service"
    )
    @ApiResponse(responseCode = "200", description = "Service is healthy and operational")
    public NotificationStatusResponse getStatus() {
        return new NotificationStatusResponse(
            "Notification Service",
            "HEALTHY",
            "Service is operational and processing events",
            List.of("order.completed", "order.cancelled")
        );
    }
}
