# ksqlDB Order Status View

This directory contains ksqlDB scripts for creating real-time order status views and monitoring capabilities for the event-driven order saga system.

## Overview

The `order_status.sql` script creates a comprehensive order status tracking system that:
- Monitors all order lifecycle events in real-time
- Maintains the current status of each order
- Provides both streaming (PUSH) and point-in-time (PULL) query capabilities
- Includes operational monitoring and alerting streams

## Architecture

The script implements a multi-layer architecture:

```
Order Events (Kafka Topics)
     ↓
Stream Declarations (Avro)
     ↓
Unified Event Stream
     ↓
Order Status Table (Latest Status per Order)
     ↓
Query Layer (PUSH/PULL queries)
```

## Prerequisites

1. **Kafka Cluster** with Schema Registry running
2. **ksqlDB Server** and CLI containers
3. **Avro Schemas** deployed to Schema Registry (from `common-avro` module)
4. **Order saga services** producing events to the defined topics

## Getting Started

### 1. Start Infrastructure

Ensure your Kafka infrastructure is running:

```bash
cd infra
docker-compose up -d
```

### 2. Connect to ksqlDB

Access the ksqlDB CLI:

```bash
docker exec -it ksqldb-cli ksql http://ksqldb-server:8088
```

### 3. Execute the Script

You can either:

**Option A: Copy-paste sections** from `order_status.sql` into the CLI

**Option B: Run the entire script** (if file is mounted in container):
```sql
RUN SCRIPT '/path/to/order_status.sql';
```

**Option C: Execute line by line** for better understanding and debugging

### 4. Verify Setup

Check that streams and tables were created:

```sql
SHOW STREAMS;
SHOW TABLES;
DESCRIBE order_status;
```

## Key Components

### Streams

The script creates streams for all major order saga events:

- `order_created_stream` - New orders
- `payment_authorized_stream` / `payment_failed_stream` - Payment events
- `inventory_reserved_stream` / `inventory_rejected_stream` - Inventory events
- `shipping_scheduled_stream` - Shipping events
- `order_completed_stream` / `order_cancelled_stream` - Final states

### Unified Event Stream

`order_events` - Combines all order events with standardized status values:
- `CREATED`
- `PAYMENT_AUTHORIZED` / `PAYMENT_FAILED`
- `INVENTORY_RESERVED` / `INVENTORY_REJECTED`
- `SHIPPING_SCHEDULED`
- `COMPLETED` / `CANCELLED`

### Order Status Table

`order_status` - Maintains latest state per order:
- `orderId` - Primary key
- `currentStatus` - Latest status
- `userId` - Order owner
- `orderTotal` - Order value
- `orderItems` - Order items
- `lastUpdated` - Last status change timestamp
- `lastStatusDetails` - Additional context
- `totalStatusChanges` - Number of status transitions

## Query Examples

### PUSH Queries (Real-time Streaming)

**Monitor all order status changes:**
```sql
SELECT * FROM order_events EMIT CHANGES;
```

**Watch for failed orders:**
```sql
SELECT * FROM order_events 
WHERE status IN ('PAYMENT_FAILED', 'INVENTORY_REJECTED', 'CANCELLED') 
EMIT CHANGES;
```

**Monitor stuck orders:**
```sql
SELECT orderId, currentStatus, lastUpdated 
FROM order_status 
WHERE currentStatus = 'CREATED' 
AND lastUpdated < (UNIX_TIMESTAMP() - 300000) 
EMIT CHANGES;
```

### PULL Queries (Point-in-time)

**Get specific order status:**
```sql
SELECT * FROM order_status WHERE orderId = 'order-123';
```

**Get orders by status:**
```sql
SELECT * FROM order_status WHERE currentStatus = 'PAYMENT_AUTHORIZED';
```

**Status summary:**
```sql
SELECT currentStatus, COUNT(*) as count 
FROM order_status 
GROUP BY currentStatus;
```

## Operational Monitoring

The script includes additional monitoring streams:

### Problematic Orders Stream
Detects orders with excessive status changes:
```sql
SELECT * FROM problematic_orders EMIT CHANGES;
```

### Payment Stuck Alerts
Alerts for orders stuck in payment phase:
```sql
SELECT * FROM payment_stuck_alerts EMIT CHANGES;
```

## Common Use Cases

### 1. Order Status Dashboard
Query current status of all orders for dashboard display:
```sql
SELECT orderId, currentStatus, orderTotal, lastUpdated 
FROM order_status;
```

### 2. Failed Order Analysis
Analyze patterns in failed orders:
```sql
SELECT * FROM order_events 
WHERE status IN ('PAYMENT_FAILED', 'INVENTORY_REJECTED') 
EMIT CHANGES;
```

### 3. Performance Monitoring
Track order processing times:
```sql
SELECT 
    orderId,
    currentStatus,
    totalStatusChanges,
    (UNIX_TIMESTAMP() - lastUpdated) / 1000 AS seconds_in_current_state
FROM order_status;
```

### 4. Business Intelligence
Extract order completion rates:
```sql
SELECT 
    currentStatus,
    COUNT(*) as order_count,
    CAST(COUNT(*) AS DOUBLE) / (SELECT COUNT(*) FROM order_status) * 100 AS percentage
FROM order_status 
GROUP BY currentStatus;
```

## Troubleshooting

### Common Issues

1. **Schema Registry Not Available**
   - Ensure Schema Registry is running and accessible
   - Verify Avro schemas are registered

2. **Topics Don't Exist**
   - Check that your services are producing events
   - Verify topic names match your Kafka configuration

3. **No Data Flowing**
   - Check if your services are running and producing events
   - Verify topic partitioning and consumer group settings

### Debugging Commands

```sql
-- Check stream/table status
DESCRIBE EXTENDED order_events;
DESCRIBE EXTENDED order_status;

-- View running queries
SHOW QUERIES;

-- Check for errors in query logs
-- (Check ksqlDB server logs in Docker)
```

### Cleanup

To remove all created streams and tables:
```sql
-- Stop all running queries first
TERMINATE ALL;

-- Drop tables and streams (careful - this deletes data!)
DROP TABLE order_status DELETE TOPIC;
DROP STREAM order_events DELETE TOPIC;
DROP STREAM order_created_stream;
-- ... repeat for all streams
```

## Configuration

### Customization Options

1. **Topic Names**: Update topic names in WITH clauses to match your setup
2. **Partitions/Replicas**: Adjust based on your Kafka cluster size
3. **Status Values**: Modify status strings to match your business logic
4. **Alerting Thresholds**: Adjust timeouts and limits in monitoring streams

### Performance Tuning

For high-volume scenarios, consider:
- Increasing topic partitions
- Adjusting ksqlDB processing guarantees
- Implementing time-based windowing for aggregations
- Using appropriate cleanup policies

## Integration

This ksqlDB setup integrates with:
- **Order Service** - Produces OrderCreated events
- **Payment Service** - Produces payment events
- **Inventory Service** - Produces inventory events
- **Shipping Service** - Produces shipping events
- **Notification Service** - Can consume status change events
- **Order Views Service** - Can query current status

## Next Steps

1. Set up monitoring dashboards using tools like Grafana
2. Create additional aggregation tables for business metrics
3. Implement more sophisticated alerting rules
4. Add data retention policies for historical data
5. Consider implementing event sourcing patterns for audit trails
