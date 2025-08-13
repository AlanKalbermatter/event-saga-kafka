package com.example.orderviews;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafkaStreams;

@SpringBootApplication
@EnableKafkaStreams
public class OrderViewsApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderViewsApplication.class, args);
    }
}
