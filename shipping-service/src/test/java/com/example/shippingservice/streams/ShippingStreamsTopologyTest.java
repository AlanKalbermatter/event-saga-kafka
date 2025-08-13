package com.example.shippingservice.streams;

import com.example.events.InventoryReserved;
import com.example.events.PaymentAuthorized;
import com.example.events.ReservedItem;
import com.example.events.ShippingScheduled;
import com.example.shippingservice.service.ShippingService;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.test.TestRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShippingStreamsTopologyTest {

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, PaymentAuthorized> paymentTopic;
    private TestInputTopic<String, InventoryReserved> inventoryTopic;
    private TestOutputTopic<String, ShippingScheduled> shippingTopic;

    @Mock
    private ShippingService shippingService;

    private final String schemaRegistryUrl = "mock://test";

    @BeforeEach
    void setUp() {
        // Create topology
        ShippingStreamsTopology topologyConfig = new ShippingStreamsTopology();
        // Use reflection to set the schema registry URL and shipping service
        setField(topologyConfig, "schemaRegistryUrl", schemaRegistryUrl);
        setField(topologyConfig, "shippingService", shippingService);

        StreamsBuilder builder = new StreamsBuilder();
        topologyConfig.shippingTopology(builder);
        Topology topology = builder.build();

        // Configure test driver
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);

        testDriver = new TopologyTestDriver(topology, props);

        // Configure Avro Serdes
        final Map<String, String> serdeConfig = Map.of(
            AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl
        );

        final SpecificAvroSerde<PaymentAuthorized> paymentSerde = new SpecificAvroSerde<>();
        paymentSerde.configure(serdeConfig, false);

        final SpecificAvroSerde<InventoryReserved> inventorySerde = new SpecificAvroSerde<>();
        inventorySerde.configure(serdeConfig, false);

        final SpecificAvroSerde<ShippingScheduled> shippingSerde = new SpecificAvroSerde<>();
        shippingSerde.configure(serdeConfig, false);

        // Create test topics
        paymentTopic = testDriver.createInputTopic("payment.authorized",
            Serdes.String().serializer(), paymentSerde.serializer());
        inventoryTopic = testDriver.createInputTopic("inventory.reserved",
            Serdes.String().serializer(), inventorySerde.serializer());
        shippingTopic = testDriver.createOutputTopic("shipping.scheduled",
            Serdes.String().deserializer(), shippingSerde.deserializer());
    }

    @AfterEach
    void tearDown() {
        if (testDriver != null) {
            testDriver.close();
        }
    }

    @Test
    void shouldJoinPaymentAndInventoryEvents() {
        // Given
        String orderId = "order-123";

        PaymentAuthorized payment = PaymentAuthorized.newBuilder()
            .setOrderId(orderId)
            .setPaymentId("payment-456")
            .setAmount(100.0)
            .setCurrency("USD")
            .setAuthorizedAt(Instant.now().toString())
            .build();

        InventoryReserved inventory = InventoryReserved.newBuilder()
            .setOrderId(orderId)
            .setItems(List.of(
                ReservedItem.newBuilder()
                    .setSku("SKU-123")
                    .setQty(2)
                    .build()
            ))
            .setReservedAt(Instant.now().toString())
            .build();

        ShippingScheduled expectedShipping = ShippingScheduled.newBuilder()
            .setOrderId(orderId)
            .setShipmentId("SH-12345678")
            .setCarrier("FedEx")
            .setEta(Instant.now().plus(Duration.ofDays(2)).toString())
            .setScheduledAt(Instant.now().toString())
            .build();

        when(shippingService.scheduleShipping(any(PaymentAuthorized.class), any(InventoryReserved.class)))
            .thenReturn(expectedShipping);

        // When
        paymentTopic.pipeInput(orderId, payment);
        inventoryTopic.pipeInput(orderId, inventory);

        // Then
        TestRecord<String, ShippingScheduled> result = shippingTopic.readRecord();
        assertNotNull(result);
        assertEquals(orderId, result.key());
        assertEquals(expectedShipping.getOrderId(), result.value().getOrderId());
        assertEquals(expectedShipping.getShipmentId(), result.value().getShipmentId());
        assertEquals(expectedShipping.getCarrier(), result.value().getCarrier());
    }

    @Test
    void shouldNotEmitShippingWhenOnlyPaymentReceived() {
        // Given
        String orderId = "order-123";

        PaymentAuthorized payment = PaymentAuthorized.newBuilder()
            .setOrderId(orderId)
            .setPaymentId("payment-456")
            .setAmount(100.0)
            .setCurrency("USD")
            .setAuthorizedAt(Instant.now().toString())
            .build();

        // When
        paymentTopic.pipeInput(orderId, payment);

        // Then
        assertTrue(shippingTopic.isEmpty());
    }

    @Test
    void shouldNotEmitShippingWhenOnlyInventoryReceived() {
        // Given
        String orderId = "order-123";

        InventoryReserved inventory = InventoryReserved.newBuilder()
            .setOrderId(orderId)
            .setItems(List.of(
                ReservedItem.newBuilder()
                    .setSku("SKU-123")
                    .setQty(2)
                    .build()
            ))
            .setReservedAt(Instant.now().toString())
            .build();

        // When
        inventoryTopic.pipeInput(orderId, inventory);

        // Then
        assertTrue(shippingTopic.isEmpty());
    }

    @Test
    void shouldNotJoinEventsOutsideTimeWindow() {
        // Given
        String orderId = "order-123";

        PaymentAuthorized payment = PaymentAuthorized.newBuilder()
            .setOrderId(orderId)
            .setPaymentId("payment-456")
            .setAmount(100.0)
            .setCurrency("USD")
            .setAuthorizedAt(Instant.now().toString())
            .build();

        InventoryReserved inventory = InventoryReserved.newBuilder()
            .setOrderId(orderId)
            .setItems(List.of(
                ReservedItem.newBuilder()
                    .setSku("SKU-123")
                    .setQty(2)
                    .build()
            ))
            .setReservedAt(Instant.now().toString())
            .build();

        // When - send payment first
        paymentTopic.pipeInput(orderId, payment);

        // Advance time beyond the 15-minute window
        testDriver.advanceWallClockTime(Duration.ofMinutes(16));

        // Send inventory event
        inventoryTopic.pipeInput(orderId, inventory);

        // Then
        assertTrue(shippingTopic.isEmpty());
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
