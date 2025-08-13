# Shipping Service

The Shipping Service is a **Kafka Streams** application that implements the final step of an event-driven e-commerce saga pattern. It waits for both payment authorization and inventory reservation events, then schedules shipping when both conditions are met within a 15-minute time window.

## Features

- **Event-Driven Architecture**: Uses Kafka Streams to process payment and inventory events
- **Stream Join with Time Window**: Joins `payment.authorized` and `inventory.reserved` events within a 15-minute window
- **Exactly-Once Semantics V2**: Configured with `processing.guarantee: exactly_once_v2` for reliable processing
- **State Store Materialization**: Maintains a KTable for order status monitoring
- **REST API**: Exposes endpoints for order status queries and health checks
- **Metrics & Observability**: Integrated with Spring Boot Actuator and Micrometer for monitoring

## Architecture

### Kafka Streams Topology

```
payment.authorized ─┐
                    ├─ JOIN (15-minute window) ─> shipping.scheduled
inventory.reserved ─┘
                              ↓
                    order-status-table (KTable)
```

### Event Flow

1. **Input Events**:
   - `payment.authorized` - Payment has been successfully authorized
   - `inventory.reserved` - Inventory has been reserved for the order

2. **Processing**:
   - Events are joined by `orderId` within a 15-minute time window
   - When both events are present, a `ShippingScheduled` event is created
   - Order status is materialized in a state store for querying

3. **Output Events**:
   - `shipping.scheduled` - Shipping has been scheduled with shipment details

## Configuration

### Kafka Streams Settings

- **Application ID**: `shipping-service`
- **Processing Guarantee**: `exactly_once_v2`
- **Window Store Retention**: 15 minutes (900,000 ms)
- **Commit Interval**: 1 second
- **Stream Threads**: 2

### Topics

- **Input**: `payment.authorized`, `inventory.reserved`
- **Output**: `shipping.scheduled`

## API Endpoints

### Order Status Query
```http
GET /api/shipping/status/{orderId}
```

Returns the current shipping status for an order.

**Response**:
```json
{
  "orderId": "order-123",
  "status": "SHIPPING_SCHEDULED" | "NOT_FOUND"
}
```

### Health Check
```http
GET /api/shipping/health
```

Returns the health status of the Kafka Streams application.

**Response**:
```json
{
  "status": "UP" | "DOWN",
  "kafkaStreamsState": "RUNNING" | "REBALANCING" | "ERROR"
}
```

### Actuator Endpoints
```http
GET /actuator/health
GET /actuator/metrics
GET /actuator/prometheus
GET /actuator/kafkastreams
```

## Shipping Logic

The service implements intelligent carrier selection based on order characteristics:

- **High-value orders** (>$500): FedEx (premium service)
- **Bulk orders** (>10 items): UPS (good for large shipments)
- **Small orders** (<$50): USPS (economical)
- **Other orders**: Random selection from available carriers

### Carriers
- FedEx (1-3 days)
- UPS (2-4 days)
- DHL (3-4 days)
- USPS (3-5 days)
- Amazon Logistics (1-2 days)

## Running the Service

### Prerequisites
- Java 21
- Apache Kafka with Schema Registry
- Topics: `payment.authorized`, `inventory.reserved`

### Configuration
Set the following environment variables:
- `KAFKA_BOOTSTRAP_SERVERS` (default: localhost:9092)
- `SCHEMA_REGISTRY_URL` (default: http://localhost:8081)

### Start the Service
```bash
mvn spring-boot:run -pl shipping-service
```

The service will start on port **8083**.

## Monitoring

The service exposes comprehensive metrics through:

- **Kafka Streams Metrics**: Stream processing metrics, state store metrics
- **Spring Boot Actuator**: Health checks, application metrics
- **Micrometer + Prometheus**: Custom business metrics with Prometheus format
- **Structured Logging**: JSON-formatted logs with correlation IDs

### Key Metrics to Monitor

- `kafka.streams.stream.thread.state` - Stream thread health
- `kafka.streams.state.store.get.rate` - State store query rate
- `shipping.events.joined.total` - Successful event joins
- `shipping.events.scheduled.total` - Shipping events created

## Error Handling

- **Deserialization Errors**: LogAndContinueExceptionHandler - logs and continues
- **Stream Processing Errors**: Uncaught exceptions trigger application shutdown
- **Avro Schema Evolution**: Supports backward/forward compatible schema changes

## Testing

Run unit tests:
```bash
mvn test -pl shipping-service
```

The test suite includes:
- Kafka Streams topology testing with TopologyTestDriver
- Event join scenarios within and outside time windows
- Error condition handling
- State store query testing

## Time Window Behavior

The 15-minute join window means:
- ✅ Payment at 10:00, Inventory at 10:10 → Join successful
- ✅ Inventory at 10:00, Payment at 10:14 → Join successful  
- ❌ Payment at 10:00, Inventory at 10:16 → No join (outside window)

This ensures orders are processed in a timely manner while allowing for some system latency.
