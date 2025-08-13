# Event-Driven E-Commerce with Kafka Sagas

A microservices-based e-commerce system implementing the Saga pattern with choreography using Apache Kafka for event-driven architecture.

## 🏗️ Architecture Overview

This system demonstrates distributed transaction management using the **Saga Choreography** pattern across multiple microservices:

- **Order Service** - Manages order lifecycle
- **Payment Service** - Handles payment processing
- **Inventory Service** - Manages stock and reservations
- **Shipping Service** - Coordinates delivery
- **Notification Service** - Sends customer notifications
- **Order Views Service** - Kafka Streams for order aggregation and views

## 🚀 Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 21
- Maven 3.8+

### Run the System
```bash
# Start infrastructure (Kafka, Zookeeper, Schema Registry)
docker-compose up -d

# Build all services
mvn clean install

# Start all microservices
mvn spring-boot:run -pl order-service &
mvn spring-boot:run -pl payment-service &
mvn spring-boot:run -pl inventory-service &
mvn spring-boot:run -pl shipping-service &
mvn spring-boot:run -pl notification-service &
mvn spring-boot:run -pl order-views-service &
```

## 📝 Creating an Order

### Submit Order Request
```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "customer-123",
    "items": [
      {
        "productId": "product-456",
        "quantity": 2,
        "price": 99.99
      }
    ]
  }'
```

### Response
```json
{
  "orderId": "order-789",
  "status": "PENDING",
  "totalAmount": 199.98
}
```

## 🔄 Event Flow

### Happy Path Flow
```
1. OrderCreated → Order Service
2. InventoryReserved → Inventory Service
3. PaymentProcessed → Payment Service
4. ShippingArranged → Shipping Service
5. OrderCompleted → Order Service
6. NotificationSent → Notification Service
```

### Compensation Flow (Payment Failure)
```
1. OrderCreated → Order Service
2. InventoryReserved → Inventory Service
3. PaymentFailed → Payment Service
4. InventoryReleased → Inventory Service (compensation)
5. OrderCancelled → Order Service (compensation)
6. NotificationSent → Notification Service
```

## 📊 Event Topics

| Topic | Producer | Consumer | Event Type |
|-------|----------|----------|------------|
| `order-events` | Order Service | All Services | OrderCreated, OrderCompleted, OrderCancelled |
| `inventory-events` | Inventory Service | Order, Shipping | InventoryReserved, InventoryReleased |
| `payment-events` | Payment Service | Order, Notification | PaymentProcessed, PaymentFailed |
| `shipping-events` | Shipping Service | Order, Notification | ShippingArranged, ShippingFailed |
| `notification-events` | Notification Service | - | NotificationSent |

## 🎯 Saga Choreography Pattern

Each service listens to relevant events and publishes new events based on business logic:

### Order Service Saga States
- `PENDING` → `INVENTORY_RESERVED` → `PAYMENT_PROCESSED` → `SHIPPED` → `COMPLETED`
- Compensation: `PAYMENT_FAILED` → `CANCELLED`

### Key Benefits
- **Decentralized coordination** - No central orchestrator
- **Loose coupling** - Services communicate via events
- **Fault tolerance** - Automatic compensation handling
- **Scalability** - Independent service scaling

## 🔍 Monitoring Order Status

### Check Order Details
```bash
curl http://localhost:8081/order-views/orders/order-789
```

### List All Orders
```bash
curl http://localhost:8081/order-views/orders
```

## 🏃‍♂️ Service Endpoints

| Service | Port | Health Check |
|---------|------|--------------|
| Order Service | 8080 | `GET /actuator/health` |
| Payment Service | 8082 | `GET /actuator/health` |
| Inventory Service | 8083 | `GET /actuator/health` |
| Shipping Service | 8084 | `GET /actuator/health` |
| Notification Service | 8085 | `GET /actuator/health` |
| Order Views Service | 8081 | `GET /actuator/health` |

## 🧪 Testing Scenarios

### Test Inventory Shortage
```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "customer-123",
    "items": [{"productId": "out-of-stock", "quantity": 1, "price": 50.00}]
  }'
```

### Test Payment Failure
```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "invalid-payment-customer",
    "items": [{"productId": "product-456", "quantity": 1, "price": 99.99}]
  }'
```

## 🛠️ Technology Stack

- **Spring Boot 3.5** - Microservices framework
- **Apache Kafka** - Event streaming platform
- **Kafka Streams** - Stream processing for order views
- **Avro** - Schema evolution for events
- **Docker** - Containerization
- **Maven** - Build and dependency management

## 📋 Development

### Build Individual Service
```bash
mvn clean install -pl service-name
```

### Run Tests
```bash
mvn test
```

### View Kafka Topics
```bash
docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 --list
```

## 🚨 Error Handling

The system implements comprehensive error handling:
- **Retry mechanisms** for transient failures
- **Dead letter queues** for unrecoverable messages
- **Circuit breakers** for service protection
- **Idempotency** to handle duplicate events
- **Timeout handling** for long-running operations

## 📈 Scaling Considerations

- Each service can be scaled independently
- Kafka partitions enable parallel processing
- Database per service pattern for data isolation
- Event sourcing for audit trails and replay capabilities
