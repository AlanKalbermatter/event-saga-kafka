package com.example.notificationservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Information about a supported event type")
public record EventTypeInfo(
    @Schema(description = "Kafka topic name for the event", example = "order.completed")
    String topicName,

    @Schema(description = "Event type name", example = "OrderCompleted")
    String eventName,

    @Schema(description = "Description of what this event represents and what notification is sent")
    String description,

    @Schema(description = "Fully qualified Java class name of the Avro schema", example = "com.example.events.OrderCompleted")
    String schemaClass
) {}
