package com.example.notificationservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Status information for the notification service")
public record NotificationStatusResponse(
    @Schema(description = "Service name", example = "Notification Service")
    String serviceName,

    @Schema(description = "Current service status", example = "HEALTHY")
    String status,

    @Schema(description = "Additional status message")
    String message,

    @Schema(description = "List of topics being monitored")
    List<String> monitoredTopics
) {}
