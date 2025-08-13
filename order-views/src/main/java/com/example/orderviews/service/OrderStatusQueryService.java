package com.example.orderviews.service;

import com.example.orderviews.model.OrderStatus;
import com.example.orderviews.streams.OrderStatusTopology;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Service;

@Service
public class OrderStatusQueryService {

    private final StreamsBuilderFactoryBean streamsBuilderFactoryBean;

    @Autowired
    public OrderStatusQueryService(StreamsBuilderFactoryBean streamsBuilderFactoryBean) {
        this.streamsBuilderFactoryBean = streamsBuilderFactoryBean;
    }

    public OrderStatus getOrderStatus(String orderId) {
        KafkaStreams kafkaStreams = streamsBuilderFactoryBean.getKafkaStreams();

        if (kafkaStreams == null) {
            throw new IllegalStateException("Kafka Streams is not available");
        }

        ReadOnlyKeyValueStore<String, OrderStatus> store = kafkaStreams.store(
            StoreQueryParameters.fromNameAndType(
                OrderStatusTopology.ORDER_STATUS_STORE,
                QueryableStoreTypes.keyValueStore()
            )
        );

        return store.get(orderId);
    }
}
