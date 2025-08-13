# Order Views Service

## Overview

The Order Views service is a Kafka Streams application that builds materialized views of order status by consuming events from multiple topics in the event-driven saga architecture. It provides a read model with Interactive Queries for real-time order status lookups.

## Features

- **Materialized Views**: Builds KTable from order-related events stored in RocksDB
- **Exactly-Once Semantics**: Configured with `exactly_once_v2` processing guarantee
- **Interactive Queries**: REST API to query local state store
- **Event Processing**: Handles the complete order lifecycle events:
  - `order.created` → CREATED
  - `payment.authorized/payment.failed` → PAYMENT_AUTHORIZED/PAYMENT_FAILED
  - `inventory.reserved/inventory.rejected` → INVENTORY_RESERVED/INVENTORY_REJECTED
  - `shipping.scheduled` → SHIPPING_SCHEDULED
  - `order.completed/order.cancelled` → COMPLETED/CANCELLED

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Kafka Topics  │───▶│  Kafka Streams   │───▶│   RocksDB       │
│                 │    │  Topology        │    │   State Store   │
│ - order.created │    │                  │    │                 │
│ - payment.*     │    │ ┌──────────────┐ │    │ ┌─────────────┐ │
│ - inventory.*   │    │ │ Merge Events │ │    │ │ OrderStatus │ │
│ - shipping.*    │    │ │ & Aggregate  │ │    │ │   KTable    │ │
│ - order.*       │    │ └──────────────┘ │    │ └─────────────┘ │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                 │
                                 ▼
                       ┌──────────────────┐
                       │   REST API       │
                       │ Interactive      │
                       │ Queries          │
                       └──────────────────┘
```

## API Endpoints

### Order Status Query
```http
GET /orders/{orderId}/status
```

**Response:**
```json
{
  "orderId": "order-123",
  "status": "PAYMENT_AUTHORIZED",
  "userId": "user-456",
  "total": 99.99,
  "lastUpdated": "2024-01-15T10:30:00Z",
  "paymentId": "payment-789",
  "failureReason": null
}
```

### Health Check
```http
GET /health
```

### Metrics (Prometheus)
```http
GET /actuator/prometheus
```

## Configuration

Key Kafka Streams configurations in `application.yml`:

```yaml
spring:
  kafka:
    streams:
      application-id: order-views-service
      properties:
        processing.guarantee: exactly_once_v2
        num.stream.threads: 2
        state.dir: /tmp/kafka-streams/order-views
```

## Order Status Lifecycle

1. **CREATED** - Order is created
2. **PAYMENT_AUTHORIZED** - Payment successfully processed
3. **PAYMENT_FAILED** - Payment processing failed (terminal state)
4. **INVENTORY_RESERVED** - Inventory allocated
5. **INVENTORY_REJECTED** - Insufficient inventory (leads to cancellation)
6. **SHIPPING_SCHEDULED** - Shipping arranged
7. **COMPLETED** - Order fulfilled successfully
8. **CANCELLED** - Order cancelled due to failures

## Running the Service

### Prerequisites
- Kafka cluster running (via docker-compose)
- Schema Registry available
- Required topics created (see `infra/topics.sh`)

### Start Service
```bash
mvn spring-boot:run
```

### Docker
```bash
docker build -t order-views .
docker run -p 8086:8086 order-views
```

## State Store

- **Store Name**: `order-status-store`
- **Type**: RocksDB KeyValue Store
- **Key**: Order ID (String)
- **Value**: OrderStatus (JSON)
- **Changelog Topic**: `order-views-store-changelog`

## Monitoring

The service exposes metrics via Micrometer/Prometheus:
- Kafka Streams metrics
- Custom business metrics
- JVM and application health metrics

Access metrics at: `http://localhost:8086/actuator/prometheus`

## Testing

Run tests with:
```bash
mvn test
```

For integration tests with Testcontainers:
```bash
mvn verify
```

## Error Handling

- **Processing Guarantee**: Exactly-once semantics prevent duplicate processing
- **State Recovery**: Automatic state restoration from changelog topics
- **Failure Modes**: Service gracefully handles temporary Kafka unavailability
