# Observability and Monitoring Integration

This document describes the comprehensive observability enhancements added to all microservices in the event-saga-kafka project. These changes enable distributed tracing, metrics collection, structured logging, and health monitoring across the entire system.

## Overview

The following observability features have been integrated into all 6 microservices:
- **Order Service** (`order-service`)
- **Payment Service** (`payment-service`) 
- **Inventory Service** (`inventory-service`)
- **Shipping Service** (`shipping-service`)
- **Notification Service** (`notification-service`)
- **Order Views Service** (`order-views`)

## Features Added

### 🎯 Micrometer Prometheus Registry
- Exports application metrics in Prometheus format
- Includes HTTP request metrics with percentiles (50%, 90%, 95%, 99%)
- Custom business metrics support
- Endpoint: `/actuator/prometheus`

### 🔍 OpenTelemetry Distributed Tracing
- OTLP (OpenTelemetry Protocol) exporter integration
- Automatic trace propagation through HTTP requests
- Kafka message header-based trace propagation
- Integration with Jaeger, Zipkin, and cloud tracing services

### 📊 Spring Boot Actuator Health Endpoints
- Comprehensive health checks: `/actuator/health`
- Metrics endpoint: `/actuator/metrics` 
- Application info: `/actuator/info`
- Detailed health status reporting

### 📝 Structured Logging with MDC
- Automatic addition of `traceId`, `spanId`, and `orderId` to all log messages
- Correlation ID propagation across service boundaries
- Configurable log patterns for console and file output

## Dependencies Added

Each microservice now includes these observability dependencies in their `pom.xml`:

```xml
<!-- Observability and Monitoring -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>

<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

## Configuration Changes

### Application Configuration

Each service's `application.yml` now includes comprehensive observability configuration:

```yaml
# Observability and Monitoring Configuration
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: always
    metrics:
      enabled: true
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5, 0.9, 0.95, 0.99
  tracing:
    sampling:
      probability: 1.0

# OpenTelemetry Configuration
otel:
  exporter:
    otlp:
      endpoint: http://localhost:4317
  resource:
    attributes:
      service.name: ${spring.application.name}
      service.version: @project.version@
  traces:
    exporter: otlp
  metrics:
    exporter: otlp

# Logging Configuration
logging:
  level:
    com.example.[SERVICE_NAME]: DEBUG
    io.micrometer: INFO
    io.opentelemetry: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [%X{traceId:-},%X{spanId:-}] [orderId:%X{orderId:-}] %logger{36} - %msg%n"
```

### Infrastructure Components

#### 1. MDC Logging Filter

Location: Each service has `MdcLoggingFilter.java` in its config package
- Automatically adds correlation IDs to MDC
- Extracts `orderId` from HTTP headers, parameters, or URL paths
- Ensures proper cleanup to prevent memory leaks

#### 2. Kafka Tracing Configuration

Location: Each service has `KafkaTracingConfig.java`
- **Producer Interceptor**: Adds trace headers (`X-Trace-Id`, `X-Span-Id`, `X-Order-Id`) to outgoing Kafka messages
- **Consumer Interceptor**: Extracts trace headers from incoming messages and adds to MDC
- Enables end-to-end tracing through asynchronous message flows

#### 3. OpenTelemetry Configuration

Location: Each service has `ObservabilityConfig.java`
- Configures OTLP exporter with proper resource attributes
- Custom health indicators for service-specific checks
- Integration with Spring Boot's tracing infrastructure

## Shared Components

### Common Observability Library

Location: `infra/spring-kafka-starter/src/main/java/com/example/common/observability/`

#### CommonMdcLoggingFilter
Reusable MDC filter that can be imported by any service:
```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CommonMdcLoggingFilter implements Filter
```

#### CommonKafkaTracingConfig
Shared Kafka tracing utilities:
```java
@Configuration
public class CommonKafkaTracingConfig {
    // TracingProducerInterceptor
    // TracingConsumerInterceptor  
    // Utility methods for header extraction
}
```

## Exposed Endpoints

Each microservice now exposes these observability endpoints:

### Health and Status
- `GET /actuator/health` - Comprehensive health status
- `GET /actuator/info` - Application information

### Metrics and Monitoring  
- `GET /actuator/metrics` - Available metrics list
- `GET /actuator/prometheus` - Prometheus-formatted metrics
- `GET /actuator/metrics/{metric-name}` - Specific metric details

## Structured Logging Format

All log messages now include correlation identifiers:

```
2024-01-15 10:30:45 [http-nio-8080-exec-1] INFO [traceId:abc123def456,spanId:789xyz] [orderId:order-12345] com.example.orderservice.service.OrderService - Processing order creation request
```

### MDC Keys
- `traceId` - Distributed trace identifier
- `spanId` - Current span identifier  
- `orderId` - Business correlation identifier extracted from requests

## Distributed Tracing Flow

### HTTP Request Tracing
1. Incoming HTTP request automatically gets a trace context
2. MDC filter adds trace IDs to logging context
3. Outgoing HTTP calls propagate trace context via headers

### Kafka Message Tracing  
1. Producer interceptor adds trace headers to outgoing messages
2. Consumer interceptor extracts headers and updates MDC
3. Trace context flows through the entire saga workflow

### Example Trace Flow
```
HTTP Request → Order Service → Kafka (OrderCreated) → Payment Service → Kafka (PaymentAuthorized) → Inventory Service
      ↓               ↓                    ↓                      ↓                         ↓                        ↓
  traceId:123     traceId:123         traceId:123            traceId:123              traceId:123             traceId:123
```

## Integration with Monitoring Stack

### Prometheus Integration
Configure Prometheus to scrape metrics from all services:

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'order-service'
    static_configs:
      - targets: ['order-service:8080']
    metrics_path: '/actuator/prometheus'
    
  - job_name: 'payment-service'
    static_configs:
      - targets: ['payment-service:8082']
    metrics_path: '/actuator/prometheus'
    
  # ... repeat for all services
```

### Jaeger/Zipkin Integration
Configure OTLP collector to forward traces:

```yaml
# docker-compose.yml
services:
  jaeger:
    image: jaegertracing/all-in-one:latest
    ports:
      - "16686:16686"
      - "14250:14250"
      
  otel-collector:
    image: otel/opentelemetry-collector:latest
    command: ["--config=/etc/otel-collector-config.yaml"]
    volumes:
      - ./otel-collector-config.yaml:/etc/otel-collector-config.yaml
    ports:
      - "4317:4317"   # OTLP gRPC
      - "4318:4318"   # OTLP HTTP
```

### Grafana Dashboards
Import pre-built dashboards for:
- JVM metrics (memory, GC, threads)
- HTTP request metrics (latency, throughput, errors)
- Kafka consumer/producer metrics
- Custom business metrics

## Usage Examples

### Custom Metrics
Add custom business metrics in your services:

```java
@Component
public class OrderMetrics {
    
    private final Counter orderCreatedCounter;
    private final Timer orderProcessingTimer;
    
    public OrderMetrics(MeterRegistry meterRegistry) {
        this.orderCreatedCounter = Counter.builder("orders.created.total")
                .description("Total number of orders created")
                .tag("service", "order-service")
                .register(meterRegistry);
                
        this.orderProcessingTimer = Timer.builder("orders.processing.duration")
                .description("Order processing duration")
                .register(meterRegistry);
    }
    
    public void recordOrderCreated() {
        orderCreatedCounter.increment();
    }
    
    public Timer.Sample startOrderProcessingTimer() {
        return Timer.start(orderProcessingTimer);
    }
}
```

### Manual MDC Usage
Add correlation IDs manually in service methods:

```java
@Service
public class OrderService {
    
    public void processOrder(String orderId) {
        // Add orderId to MDC for all log messages in this thread
        CommonMdcLoggingFilter.addOrderIdToMdc(orderId);
        
        try {
            // All log messages will include orderId
            log.info("Processing order"); // [orderId:order-123] Processing order
            
            // Business logic...
            
        } finally {
            // Clean up MDC
            CommonMdcLoggingFilter.removeOrderIdFromMdc();
        }
    }
}
```

## Configuration Options

### Tracing Sampling
Adjust tracing sample rate to reduce overhead in production:

```yaml
management:
  tracing:
    sampling:
      probability: 0.1  # Sample 10% of traces
```

### OTLP Endpoint Configuration
Configure different OTLP endpoints per environment:

```yaml
# Development
otel:
  exporter:
    otlp:
      endpoint: http://localhost:4317

# Production  
otel:
  exporter:
    otlp:
      endpoint: https://otlp.your-company.com:4317
      headers:
        authorization: "Bearer ${OTEL_AUTH_TOKEN}"
```

### Log Level Configuration
Fine-tune logging levels for different components:

```yaml
logging:
  level:
    com.example: INFO
    com.example.orderservice.service: DEBUG
    io.micrometer: WARN
    io.opentelemetry: WARN
    org.springframework.kafka: INFO
```

## Troubleshooting

### Common Issues

#### 1. Missing Trace Context
**Problem**: Logs don't show trace IDs
**Solution**: Ensure `micrometer-tracing-bridge-otel` dependency is present and auto-configuration is enabled

#### 2. Kafka Headers Not Propagating  
**Problem**: Trace context lost across Kafka messages
**Solution**: Verify Kafka interceptors are properly configured and headers are being added

#### 3. High Cardinality Metrics
**Problem**: Too many metric dimensions causing memory issues
**Solution**: Review metric tags and use bounded tag values

#### 4. OTLP Connection Issues
**Problem**: Traces not appearing in external systems
**Solution**: Check OTLP endpoint connectivity and authentication

### Debug Commands

Check actuator endpoints:
```bash
# Health check
curl http://localhost:8080/actuator/health

# Available metrics
curl http://localhost:8080/actuator/metrics

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# Specific metric
curl http://localhost:8080/actuator/metrics/http.server.requests
```

## Performance Considerations

### Metrics Collection Overhead
- Histogram metrics have higher memory overhead
- Consider reducing percentile calculations in high-throughput scenarios
- Use sampling for expensive custom metrics

### Tracing Overhead
- Full sampling (probability: 1.0) adds ~5-10% overhead
- Reduce sampling rate in production environments
- Async span processing minimizes latency impact

### Logging Performance
- Structured logging has minimal overhead
- Avoid expensive MDC operations in hot paths
- Use appropriate log levels to reduce I/O

## Next Steps

1. **Deploy Infrastructure**: Set up Prometheus, Grafana, and Jaeger/Zipkin
2. **Configure Alerting**: Create alerts based on health endpoints and metrics
3. **Build Dashboards**: Import/create Grafana dashboards for monitoring
4. **Log Aggregation**: Configure ELK stack or similar for centralized logging
5. **Performance Tuning**: Adjust sampling rates and metric collection based on load

## Related Documentation

- [Spring Boot Actuator Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Documentation](https://micrometer.io/docs)
- [OpenTelemetry Java Documentation](https://opentelemetry.io/docs/instrumentation/java/)
- [Prometheus Integration Guide](https://prometheus.io/docs/instrumenting/clientlibs/)

---

The observability infrastructure is now fully integrated and ready to provide comprehensive monitoring, distributed tracing, and structured logging across your entire event-driven saga system.
