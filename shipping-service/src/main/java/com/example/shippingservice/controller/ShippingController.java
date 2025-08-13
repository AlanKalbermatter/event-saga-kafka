package com.example.shippingservice.controller;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/shipping")
public class ShippingController {

    private static final Logger log = LoggerFactory.getLogger(ShippingController.class);

    @Autowired
    private StreamsBuilderFactoryBean streamsBuilderFactoryBean;

    @GetMapping("/status/{orderId}")
    public ResponseEntity<Map<String, String>> getOrderStatus(@PathVariable String orderId) {
        try {
            KafkaStreams kafkaStreams = streamsBuilderFactoryBean.getKafkaStreams();

            if (kafkaStreams == null || kafkaStreams.state() != KafkaStreams.State.RUNNING) {
                return ResponseEntity.status(503)
                    .body(Map.of("error", "Kafka Streams not available"));
            }

            ReadOnlyKeyValueStore<String, String> store = kafkaStreams
                .store(StoreQueryParameters.fromNameAndType("order-status-table", QueryableStoreTypes.keyValueStore()));

            String status = store.get(orderId);

            Map<String, String> response = new HashMap<>();
            response.put("orderId", orderId);
            response.put("status", status != null ? status : "NOT_FOUND");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error querying order status for orderId: {}", orderId, e);
            return ResponseEntity.status(500)
                .body(Map.of("error", "Failed to query order status"));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();

        try {
            KafkaStreams kafkaStreams = streamsBuilderFactoryBean.getKafkaStreams();

            if (kafkaStreams != null) {
                health.put("kafkaStreamsState", kafkaStreams.state().toString());
                health.put("status", kafkaStreams.state() == KafkaStreams.State.RUNNING ? "UP" : "DOWN");
            } else {
                health.put("status", "DOWN");
                health.put("kafkaStreamsState", "NULL");
            }

        } catch (Exception e) {
            log.error("Error checking health", e);
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
        }

        return ResponseEntity.ok(health);
    }
}
