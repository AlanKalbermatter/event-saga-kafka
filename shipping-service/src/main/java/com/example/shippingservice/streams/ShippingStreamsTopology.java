package com.example.shippingservice.streams;

import com.example.events.InventoryReserved;
import com.example.events.PaymentAuthorized;
import com.example.events.ShippingScheduled;
import com.example.shippingservice.service.ShippingService;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafkaStreams
public class ShippingStreamsTopology {

    private static final Logger log = LoggerFactory.getLogger(ShippingStreamsTopology.class);

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Autowired
    private ShippingService shippingService;

    @Bean
    public KStream<String, ShippingScheduled> shippingTopology(StreamsBuilder streamsBuilder) {
        // Configure Avro Serdes
        final Map<String, Object> serdeConfig = new HashMap<>();
        serdeConfig.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        serdeConfig.put("specific.avro.reader", true);

        final Serde<PaymentAuthorized> paymentSerde = createAvroSerde(serdeConfig);
        final Serde<InventoryReserved> inventorySerde = createAvroSerde(serdeConfig);
        final Serde<ShippingScheduled> shippingSerde = createAvroSerde(serdeConfig);

        // Create KStreams for input topics
        KStream<String, PaymentAuthorized> paymentStream = streamsBuilder
            .stream("payment.authorized", Consumed.with(Serdes.String(), paymentSerde))
            .peek((key, value) -> log.debug("Received payment authorized: orderId={}, paymentId={}",
                value.getOrderId(), value.getPaymentId()));

        KStream<String, InventoryReserved> inventoryStream = streamsBuilder
            .stream("inventory.reserved", Consumed.with(Serdes.String(), inventorySerde))
            .peek((key, value) -> log.debug("Received inventory reserved: orderId={}, items={}",
                value.getOrderId(), value.getItems().size()));

        // Create a state store for order status tracking
        streamsBuilder.addStateStore(
            Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore("order-status-store"),
                Serdes.String(),
                Serdes.String()
            )
        );

        // Join the two streams within a 15-minute window
        KStream<String, ShippingScheduled> shippingStream = paymentStream
            .selectKey((key, payment) -> payment.getOrderId())
            .join(
                inventoryStream.selectKey((key, inventory) -> inventory.getOrderId()),
                this::createShippingScheduled,
                JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofMinutes(15)),
                StreamJoined.with(
                    Serdes.String(),
                    paymentSerde,
                    inventorySerde
                )
            )
            .peek((orderId, shipping) -> {
                log.info("Created shipping scheduled: orderId={}, shipmentId={}, carrier={}",
                    orderId, shipping.getShipmentId(), shipping.getCarrier());
            });

        // Materialize order status as a KTable for monitoring
        KTable<String, String> orderStatusTable = shippingStream
            .map((orderId, shipping) -> KeyValue.pair(orderId, "SHIPPING_SCHEDULED"))
            .toTable(
                Materialized.<String, String, org.apache.kafka.streams.state.KeyValueStore<org.apache.kafka.common.utils.Bytes, byte[]>>as("order-status-table")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(Serdes.String())
            );

        // Send shipping scheduled events to output topic
        shippingStream.to("shipping.scheduled", Produced.with(Serdes.String(), shippingSerde));

        return shippingStream;
    }

    private ShippingScheduled createShippingScheduled(PaymentAuthorized payment, InventoryReserved inventory) {
        log.debug("Joining payment and inventory for order: {}", payment.getOrderId());

        return shippingService.scheduleShipping(payment, inventory);
    }

    @SuppressWarnings("unchecked")
    private <T extends SpecificRecord> Serde<T> createAvroSerde(Map<String, Object> serdeConfig) {
        return new Serde<T>() {
            @Override
            public Serializer<T> serializer() {
                KafkaAvroSerializer serializer = new KafkaAvroSerializer();
                serializer.configure(serdeConfig, false);
                return new Serializer<T>() {
                    @Override
                    public void configure(Map<String, ?> configs, boolean isKey) {
                        // Already configured
                    }

                    @Override
                    public byte[] serialize(String topic, T data) {
                        return serializer.serialize(topic, data);
                    }

                    @Override
                    public void close() {
                        serializer.close();
                    }
                };
            }

            @Override
            public Deserializer<T> deserializer() {
                KafkaAvroDeserializer deserializer = new KafkaAvroDeserializer();
                deserializer.configure(serdeConfig, false);
                return new Deserializer<T>() {
                    @Override
                    public void configure(Map<String, ?> configs, boolean isKey) {
                        // Already configured
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public T deserialize(String topic, byte[] data) {
                        return (T) deserializer.deserialize(topic, data);
                    }

                    @Override
                    public void close() {
                        deserializer.close();
                    }
                };
            }
        };
    }
}
