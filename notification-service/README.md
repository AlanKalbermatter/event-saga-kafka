# Notification Service

A microservice that consumes high-level order events and sends notification emails to customers.

## Features

- **Event Processing**: Consumes `order.completed` and `order.cancelled` events from Kafka
- **Idempotency**: Prevents duplicate notifications using a processed events table
- **Mock Email Service**: Logs email content instead of sending actual emails (for demo purposes)
- **OpenAPI Documentation**: REST API endpoints documenting supported event types
- **Health Monitoring**: Service status and monitoring endpoints

## Supported Events

### order.completed
- **Topic**: `order.completed`
- **Schema**: `com.example.events.OrderCompleted`
- **Action**: Sends order completion confirmation email

### order.cancelled
- **Topic**: `order.cancelled`  
- **Schema**: `com.example.events.OrderCancelled`
- **Action**: Sends order cancellation notification email

## API Endpoints

- `GET /api/notifications/event-types` - List supported event types
- `GET /api/notifications/status` - Service health status
- `GET /swagger-ui.html` - Interactive API documentation
- `GET /h2-console` - H2 database console (dev/demo only)

## Configuration

The service uses H2 in-memory database for demo purposes. In production, configure PostgreSQL or another persistent database.

Key configuration properties:
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    schema-registry-url: http://localhost:8081
```

## Running the Service

```bash
mvn spring-boot:run
```

The service will start on port 8084 by default.

## Database Schema

The service maintains a `processed_events` table to ensure idempotent processing:

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| event_id | VARCHAR | Unique event identifier |
| event_type | VARCHAR | Type of event processed |
| processed_at | TIMESTAMP | When the event was processed |

## Email Templates

The service uses simple text templates for notifications:

- **Order Completed**: Congratulates customer on successful order completion
- **Order Cancelled**: Informs customer about order cancellation with reason

In production, these would be replaced with HTML templates and actual email service integration.
