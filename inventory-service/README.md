# Inventory Service

## Overview

The Inventory Service manages stock reservations as part of the event-driven e-commerce system. It implements the **Saga pattern** with **compensation** to ensure data consistency across distributed services.

## Strategy Decision

**Event Trigger**: The service listens to `order.created` events to immediately check inventory availability when orders are placed.

**Rationale**: 
- Provides immediate feedback on inventory availability
- Prevents orders from proceeding if inventory is insufficient
- Simpler compensation: blocks payment processing rather than reversing payments
- Ensures inventory is checked as early as possible in the order lifecycle

## Compensation Strategy

When inventory reservation fails after an order is created, the service implements **compensating actions**:

1. **Inventory Rejection**: Emits `inventory.rejected` event with detailed failure reason
2. **Payment Prevention**: Emits `payment.failed` event to prevent payment processing

**Choice**: We emit `payment.failed` (with a pending payment ID) because:
- It prevents the payment service from processing the order
- Simpler than reversing an already-authorized payment
- Cleaner event flow with early failure detection

## Architecture

### Event Flow

```
order.created → [Inventory Service] → inventory.reserved (success)
                                   ↳ inventory.rejected + payment.failed (failure)
```

### Components

- **Entity**: `Inventory` - Manages SKU, available quantity, and reserved quantity
- **Repository**: `InventoryRepository` - Provides pessimistic locking for concurrent access
- **Service**: `InventoryService` - Core business logic for reservation and compensation
- **Consumers**: Handle `order.created` and retry events
- **Producers**: Emit `inventory.reserved`, `inventory.rejected`, and `payment.failed` events
- **Controller**: REST API for inventory seeding and status queries

### Database Schema

```sql
CREATE TABLE inventory (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(255) UNIQUE NOT NULL,
    available_qty INTEGER NOT NULL CHECK (available_qty >= 0),
    reserved_qty INTEGER NOT NULL DEFAULT 0 CHECK (reserved_qty >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

## Concurrency Control

- **Pessimistic Locking**: Uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` to prevent race conditions
- **Transaction Isolation**: Ensures atomic reservation operations
- **Database Constraints**: Prevents negative inventory quantities

## Error Handling & Retries

### Retry Configuration
- **3 retries** with 1-second intervals
- **Dead Letter Topic (DLT)** for failed messages after max retries
- **Separate retry consumer** for handling retry-specific logic

### Error Scenarios Handled
1. **Insufficient Stock**: Graceful rejection with compensation
2. **Non-existent SKU**: Rejection with clear error message
3. **Database Failures**: Transactional rollback and retry
4. **Kafka Failures**: Automatic retry with DLT fallback

## API Endpoints

### POST /api/inventory/seed
Seeds inventory for testing and initial setup.

```json
{
  "sku": "SKU001",
  "quantity": 100
}
```

### GET /api/inventory
Returns all inventory items with current available and reserved quantities.

### GET /api/inventory/{sku}
Returns inventory details for a specific SKU.

## Configuration

### Application Properties
```yaml
app:
  kafka:
    topics:
      order-created: order.created
      inventory-reserved: inventory.reserved
      inventory-rejected: inventory.rejected
      payment-failed: payment.failed
    consumer:
      group-id: inventory-service
```

### Database
- **Production**: PostgreSQL
- **Testing**: H2 in-memory database

## Testing

The service includes comprehensive integration tests covering:
- ✅ Successful inventory reservation
- ✅ Insufficient stock scenarios
- ✅ Non-existent SKU handling
- ✅ Inventory seeding operations
- ✅ Concurrent access patterns

## Monitoring & Observability

- **Health Checks**: Spring Actuator endpoints
- **Metrics**: Prometheus integration
- **Logging**: Structured logging with correlation IDs
- **Tracing**: Event flow tracking across services

## Business Rules

1. **Stock Availability**: Items can only be reserved if sufficient stock is available
2. **Atomic Operations**: All items in an order must be reservable, or none are reserved
3. **Early Failure**: Failed reservations prevent payment processing
4. **Idempotency**: Duplicate events are handled gracefully without side effects

## Future Enhancements

- **Multi-warehouse Support**: Extend to support inventory across multiple warehouses
- **Expiring Reservations**: Implement TTL for reservations to auto-release after timeout
- **Backorder Support**: Handle orders when stock is temporarily unavailable
- **Inventory Alerts**: Notify when stock levels fall below thresholds
