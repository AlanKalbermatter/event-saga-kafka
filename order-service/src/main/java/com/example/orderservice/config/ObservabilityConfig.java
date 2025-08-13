package com.example.orderservice.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ResourceAttributes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for observability components including OpenTelemetry and custom health indicators.
 */
@Configuration
public class ObservabilityConfig {

    @Value("${spring.application.name}")
    private String serviceName;

    @Value("${otel.exporter.otlp.endpoint:http://localhost:4317}")
    private String otlpEndpoint;

    /**
     * Configure OpenTelemetry with OTLP exporter
     */
    @Bean
    public OpenTelemetry openTelemetry() {
        Resource resource = Resource.getDefault()
                .merge(Resource.builder()
                        .put(ResourceAttributes.SERVICE_NAME, serviceName)
                        .put(ResourceAttributes.SERVICE_VERSION, "1.0.0")
                        .build());

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(
                        OtlpGrpcSpanExporter.builder()
                                .setEndpoint(otlpEndpoint)
                                .build())
                        .build())
                .setResource(resource)
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .buildAndRegisterGlobal();
    }

    /**
     * Custom health indicator for order service
     */
    @Bean
    public HealthIndicator orderServiceHealthIndicator(MeterRegistry meterRegistry) {
        return new OrderServiceHealthIndicator(meterRegistry);
    }

    /**
     * Custom health indicator implementation
     */
    public static class OrderServiceHealthIndicator implements HealthIndicator {
        private final MeterRegistry meterRegistry;
        private final Timer healthCheckTimer;

        public OrderServiceHealthIndicator(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
            this.healthCheckTimer = Timer.builder("health_check")
                    .description("Health check execution time")
                    .tag("service", "order-service")
                    .register(meterRegistry);
        }

        @Override
        public Health health() {
            try {
                return healthCheckTimer.recordCallable(() -> {
                    try {
                        // Perform basic health checks
                        checkDatabaseConnectivity();
                        checkKafkaConnectivity();

                        return Health.up()
                                .withDetail("service", "order-service")
                                .withDetail("status", "healthy")
                                .withDetail("timestamp", System.currentTimeMillis())
                                .build();
                    } catch (Exception e) {
                        return Health.down()
                                .withDetail("service", "order-service")
                                .withDetail("error", e.getMessage())
                                .withDetail("timestamp", System.currentTimeMillis())
                                .build();
                    }
                });
            } catch (Exception e) {
                return Health.down()
                        .withDetail("service", "order-service")
                        .withDetail("error", "Health check execution failed: " + e.getMessage())
                        .withDetail("timestamp", System.currentTimeMillis())
                        .build();
            }
        }

        private void checkDatabaseConnectivity() {
            // This would typically check database connectivity
            // For now, we'll assume it's healthy
        }

        private void checkKafkaConnectivity() {
            // This would typically check Kafka connectivity
            // For now, we'll assume it's healthy
        }
    }
}
