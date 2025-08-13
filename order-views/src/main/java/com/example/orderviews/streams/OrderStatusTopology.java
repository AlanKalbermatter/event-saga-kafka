package com.example.orderviews.streams;

import com.example.events.*;
import com.example.orderviews.model.OrderStatus;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class OrderStatusTopology {

    public static final String ORDER_STATUS_STORE = "order-status-store";

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Autowired
    public void buildTopology(StreamsBuilder streamsBuilder) {

        // Configure Avro Serde
        final Map<String, Object> serdeConfig = Map.of(
            "schema.registry.url", schemaRegistryUrl
        );

        final SpecificAvroSerde<OrderCreated> orderCreatedSerde = new SpecificAvroSerde<>();
        orderCreatedSerde.configure(serdeConfig, false);

        final SpecificAvroSerde<PaymentAuthorized> paymentAuthorizedSerde = new SpecificAvroSerde<>();
        paymentAuthorizedSerde.configure(serdeConfig, false);

        final SpecificAvroSerde<PaymentFailed> paymentFailedSerde = new SpecificAvroSerde<>();
        paymentFailedSerde.configure(serdeConfig, false);

        final SpecificAvroSerde<InventoryReserved> inventoryReservedSerde = new SpecificAvroSerde<>();
        inventoryReservedSerde.configure(serdeConfig, false);

        final SpecificAvroSerde<InventoryRejected> inventoryRejectedSerde = new SpecificAvroSerde<>();
        inventoryRejectedSerde.configure(serdeConfig, false);

        final SpecificAvroSerde<ShippingScheduled> shippingScheduledSerde = new SpecificAvroSerde<>();
        shippingScheduledSerde.configure(serdeConfig, false);

        final SpecificAvroSerde<OrderCompleted> orderCompletedSerde = new SpecificAvroSerde<>();
        orderCompletedSerde.configure(serdeConfig, false);

        final SpecificAvroSerde<OrderCancelled> orderCancelledSerde = new SpecificAvroSerde<>();
        orderCancelledSerde.configure(serdeConfig, false);

        // JSON Serde for OrderStatus
        final JsonSerde<OrderStatus> orderStatusSerde = new JsonSerde<>(OrderStatus.class);

        // Create streams for each event type and map them to OrderStatus
        KStream<String, OrderStatus> orderCreatedStream = streamsBuilder
            .stream("order.created", Consumed.with(Serdes.String(), orderCreatedSerde))
            .selectKey((key, orderCreated) -> orderCreated.getOrderId())
            .mapValues(this::mapOrderCreatedToStatus);

        KStream<String, OrderStatus> paymentAuthorizedStream = streamsBuilder
            .stream("payment.authorized", Consumed.with(Serdes.String(), paymentAuthorizedSerde))
            .selectKey((key, paymentAuthorized) -> paymentAuthorized.getOrderId())
            .mapValues(this::mapPaymentAuthorizedToStatus);

        KStream<String, OrderStatus> paymentFailedStream = streamsBuilder
            .stream("payment.failed", Consumed.with(Serdes.String(), paymentFailedSerde))
            .selectKey((key, paymentFailed) -> paymentFailed.getOrderId())
            .mapValues(this::mapPaymentFailedToStatus);

        KStream<String, OrderStatus> inventoryReservedStream = streamsBuilder
            .stream("inventory.reserved", Consumed.with(Serdes.String(), inventoryReservedSerde))
            .selectKey((key, inventoryReserved) -> inventoryReserved.getOrderId())
            .mapValues(this::mapInventoryReservedToStatus);

        KStream<String, OrderStatus> inventoryRejectedStream = streamsBuilder
            .stream("inventory.rejected", Consumed.with(Serdes.String(), inventoryRejectedSerde))
            .selectKey((key, inventoryRejected) -> inventoryRejected.getOrderId())
            .mapValues(this::mapInventoryRejectedToStatus);

        KStream<String, OrderStatus> shippingScheduledStream = streamsBuilder
            .stream("shipping.scheduled", Consumed.with(Serdes.String(), shippingScheduledSerde))
            .selectKey((key, shippingScheduled) -> shippingScheduled.getOrderId())
            .mapValues(this::mapShippingScheduledToStatus);

        KStream<String, OrderStatus> orderCompletedStream = streamsBuilder
            .stream("order.completed", Consumed.with(Serdes.String(), orderCompletedSerde))
            .selectKey((key, orderCompleted) -> orderCompleted.getOrderId())
            .mapValues(this::mapOrderCompletedToStatus);

        KStream<String, OrderStatus> orderCancelledStream = streamsBuilder
            .stream("order.cancelled", Consumed.with(Serdes.String(), orderCancelledSerde))
            .selectKey((key, orderCancelled) -> orderCancelled.getOrderId())
            .mapValues(this::mapOrderCancelledToStatus);

        // Merge all streams and aggregate into a KTable with state store
        orderCreatedStream
            .merge(paymentAuthorizedStream)
            .merge(paymentFailedStream)
            .merge(inventoryReservedStream)
            .merge(inventoryRejectedStream)
            .merge(shippingScheduledStream)
            .merge(orderCompletedStream)
            .merge(orderCancelledStream)
            .groupByKey()
            .aggregate(
                () -> null,
                (key, newStatus, currentStatus) -> mergeOrderStatus(currentStatus, newStatus),
                Materialized.<String, OrderStatus, KeyValueStore<org.apache.kafka.common.utils.Bytes, byte[]>>as(ORDER_STATUS_STORE)
                    .withKeySerde(Serdes.String())
                    .withValueSerde(orderStatusSerde)
            );
    }

    private OrderStatus mapOrderCreatedToStatus(OrderCreated orderCreated) {
        return OrderStatus.builder()
            .orderId(orderCreated.getOrderId())
            .status(OrderStatus.Status.CREATED)
            .userId(orderCreated.getUserId())
            .total(orderCreated.getTotal())
            .lastUpdated(Instant.parse(orderCreated.getCreatedAt()))
            .build();
    }

    private OrderStatus mapPaymentAuthorizedToStatus(PaymentAuthorized paymentAuthorized) {
        return OrderStatus.builder()
            .orderId(paymentAuthorized.getOrderId())
            .status(OrderStatus.Status.PAYMENT_AUTHORIZED)
            .paymentId(paymentAuthorized.getPaymentId())
            .lastUpdated(Instant.parse(paymentAuthorized.getAuthorizedAt()))
            .build();
    }

    private OrderStatus mapPaymentFailedToStatus(PaymentFailed paymentFailed) {
        return OrderStatus.builder()
            .orderId(paymentFailed.getOrderId())
            .status(OrderStatus.Status.PAYMENT_FAILED)
            .failureReason(paymentFailed.getReason())
            .lastUpdated(Instant.parse(paymentFailed.getFailedAt()))
            .build();
    }

    private OrderStatus mapInventoryReservedToStatus(InventoryReserved inventoryReserved) {
        return OrderStatus.builder()
            .orderId(inventoryReserved.getOrderId())
            .status(OrderStatus.Status.INVENTORY_RESERVED)
            .lastUpdated(Instant.parse(inventoryReserved.getReservedAt()))
            .build();
    }

    private OrderStatus mapInventoryRejectedToStatus(InventoryRejected inventoryRejected) {
        return OrderStatus.builder()
            .orderId(inventoryRejected.getOrderId())
            .status(OrderStatus.Status.INVENTORY_REJECTED)
            .failureReason(inventoryRejected.getReason())
            .lastUpdated(Instant.parse(inventoryRejected.getRejectedAt()))
            .build();
    }

    private OrderStatus mapShippingScheduledToStatus(ShippingScheduled shippingScheduled) {
        return OrderStatus.builder()
            .orderId(shippingScheduled.getOrderId())
            .status(OrderStatus.Status.SHIPPING_SCHEDULED)
            .lastUpdated(Instant.parse(shippingScheduled.getScheduledAt()))
            .build();
    }

    private OrderStatus mapOrderCompletedToStatus(OrderCompleted orderCompleted) {
        return OrderStatus.builder()
            .orderId(orderCompleted.getOrderId())
            .status(OrderStatus.Status.COMPLETED)
            .lastUpdated(Instant.parse(orderCompleted.getCompletedAt()))
            .build();
    }

    private OrderStatus mapOrderCancelledToStatus(OrderCancelled orderCancelled) {
        return OrderStatus.builder()
            .orderId(orderCancelled.getOrderId())
            .status(OrderStatus.Status.CANCELLED)
            .failureReason(orderCancelled.getReason())
            .lastUpdated(Instant.parse(orderCancelled.getCancelledAt()))
            .build();
    }

    // Merge function to combine order status updates while preserving existing data
    private OrderStatus mergeOrderStatus(OrderStatus existing, OrderStatus update) {
        if (existing == null) return update;
        if (update == null) return existing;

        // Always use the latest update, but preserve original order details
        return OrderStatus.builder()
            .orderId(existing.getOrderId())
            .status(update.getStatus())
            .userId(existing.getUserId() != null ? existing.getUserId() : update.getUserId())
            .total(existing.getTotal() != null ? existing.getTotal() : update.getTotal())
            .paymentId(update.getPaymentId() != null ? update.getPaymentId() : existing.getPaymentId())
            .failureReason(update.getFailureReason() != null ? update.getFailureReason() : existing.getFailureReason())
            .lastUpdated(update.getLastUpdated())
            .build();
    }
}
