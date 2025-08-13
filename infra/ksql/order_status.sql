-- ksqlDB Order Status View Script
--
-- How to run this script:
-- 1. Start your Kafka infrastructure with ksqlDB
-- 2. Connect to ksqlDB CLI: docker exec -it ksqldb-cli ksql http://ksqldb-server:8088
-- 3. Run this script by copying and pasting each section or using: RUN SCRIPT '/path/to/order_status.sql';

-- Set processing to earliest for testing (optional)
SET 'auto.offset.reset' = 'earliest';

-- =============================================================================
-- STREAM DECLARATIONS WITH AVRO SCHEMA REGISTRY
-- =============================================================================

-- Order Created Events Stream
CREATE STREAM order_created_stream (
    orderId VARCHAR,
    userId VARCHAR,
    total DOUBLE,
    items ARRAY<STRUCT<
        sku VARCHAR,
        quantity INT,
        price DOUBLE
    >>,
    timestamp BIGINT
) WITH (
    KAFKA_TOPIC = 'order-created',
    VALUE_FORMAT = 'AVRO',
    KEY_FORMAT = 'KAFKA',
    PARTITIONS = 3,
    REPLICAS = 1
);

-- Payment Events Streams
CREATE STREAM payment_authorized_stream (
    orderId VARCHAR,
    paymentId VARCHAR,
    amount DOUBLE,
    timestamp BIGINT
) WITH (
    KAFKA_TOPIC = 'payment-authorized',
    VALUE_FORMAT = 'AVRO',
    KEY_FORMAT = 'KAFKA',
    PARTITIONS = 3,
    REPLICAS = 1
);

CREATE STREAM payment_failed_stream (
    orderId VARCHAR,
    paymentId VARCHAR,
    reason VARCHAR,
    timestamp BIGINT
) WITH (
    KAFKA_TOPIC = 'payment-failed',
    VALUE_FORMAT = 'AVRO',
    KEY_FORMAT = 'KAFKA',
    PARTITIONS = 3,
    REPLICAS = 1
);

-- Inventory Events Streams
CREATE STREAM inventory_reserved_stream (
    orderId VARCHAR,
    items ARRAY<STRUCT<
        sku VARCHAR,
        quantity INT
    >>,
    timestamp BIGINT
) WITH (
    KAFKA_TOPIC = 'inventory-reserved',
    VALUE_FORMAT = 'AVRO',
    KEY_FORMAT = 'KAFKA',
    PARTITIONS = 3,
    REPLICAS = 1
);

CREATE STREAM inventory_rejected_stream (
    orderId VARCHAR,
    reason VARCHAR,
    unavailableItems ARRAY<STRUCT<
        sku VARCHAR,
        requested INT,
        available INT
    >>,
    timestamp BIGINT
) WITH (
    KAFKA_TOPIC = 'inventory-rejected',
    VALUE_FORMAT = 'AVRO',
    KEY_FORMAT = 'KAFKA',
    PARTITIONS = 3,
    REPLICAS = 1
);

-- Shipping Events Stream
CREATE STREAM shipping_scheduled_stream (
    orderId VARCHAR,
    shippingId VARCHAR,
    estimatedDelivery VARCHAR,
    timestamp BIGINT
) WITH (
    KAFKA_TOPIC = 'shipping-scheduled',
    VALUE_FORMAT = 'AVRO',
    KEY_FORMAT = 'KAFKA',
    PARTITIONS = 3,
    REPLICAS = 1
);

-- Order Completion Events Streams
CREATE STREAM order_completed_stream (
    orderId VARCHAR,
    completedAt BIGINT,
    timestamp BIGINT
) WITH (
    KAFKA_TOPIC = 'order-completed',
    VALUE_FORMAT = 'AVRO',
    KEY_FORMAT = 'KAFKA',
    PARTITIONS = 3,
    REPLICAS = 1
);

CREATE STREAM order_cancelled_stream (
    orderId VARCHAR,
    reason VARCHAR,
    timestamp BIGINT
) WITH (
    KAFKA_TOPIC = 'order-cancelled',
    VALUE_FORMAT = 'AVRO',
    KEY_FORMAT = 'KAFKA',
    PARTITIONS = 3,
    REPLICAS = 1
);

-- =============================================================================
-- UNIFIED ORDER EVENTS STREAM
-- =============================================================================

-- Create a unified stream of all order events with status
CREATE STREAM order_events AS
SELECT
    orderId,
    'CREATED' AS status,
    userId,
    total,
    items,
    timestamp,
    'Order created successfully' AS details
FROM order_created_stream
EMIT CHANGES;

-- Add payment authorized events
INSERT INTO order_events
SELECT
    orderId,
    'PAYMENT_AUTHORIZED' AS status,
    CAST(NULL AS VARCHAR) AS userId,
    amount AS total,
    CAST(NULL AS ARRAY<STRUCT<sku VARCHAR, quantity INT, price DOUBLE>>) AS items,
    timestamp,
    CONCAT('Payment authorized: ', paymentId) AS details
FROM payment_authorized_stream
EMIT CHANGES;

-- Add payment failed events
INSERT INTO order_events
SELECT
    orderId,
    'PAYMENT_FAILED' AS status,
    CAST(NULL AS VARCHAR) AS userId,
    CAST(NULL AS DOUBLE) AS total,
    CAST(NULL AS ARRAY<STRUCT<sku VARCHAR, quantity INT, price DOUBLE>>) AS items,
    timestamp,
    CONCAT('Payment failed: ', reason) AS details
FROM payment_failed_stream
EMIT CHANGES;

-- Add inventory reserved events
INSERT INTO order_events
SELECT
    orderId,
    'INVENTORY_RESERVED' AS status,
    CAST(NULL AS VARCHAR) AS userId,
    CAST(NULL AS DOUBLE) AS total,
    CAST(NULL AS ARRAY<STRUCT<sku VARCHAR, quantity INT, price DOUBLE>>) AS items,
    timestamp,
    'Inventory successfully reserved' AS details
FROM inventory_reserved_stream
EMIT CHANGES;

-- Add inventory rejected events
INSERT INTO order_events
SELECT
    orderId,
    'INVENTORY_REJECTED' AS status,
    CAST(NULL AS VARCHAR) AS userId,
    CAST(NULL AS DOUBLE) AS total,
    CAST(NULL AS ARRAY<STRUCT<sku VARCHAR, quantity INT, price DOUBLE>>) AS items,
    timestamp,
    CONCAT('Inventory rejected: ', reason) AS details
FROM inventory_rejected_stream
EMIT CHANGES;

-- Add shipping scheduled events
INSERT INTO order_events
SELECT
    orderId,
    'SHIPPING_SCHEDULED' AS status,
    CAST(NULL AS VARCHAR) AS userId,
    CAST(NULL AS DOUBLE) AS total,
    CAST(NULL AS ARRAY<STRUCT<sku VARCHAR, quantity INT, price DOUBLE>>) AS items,
    timestamp,
    CONCAT('Shipping scheduled, delivery: ', estimatedDelivery) AS details
FROM shipping_scheduled_stream
EMIT CHANGES;

-- Add order completed events
INSERT INTO order_events
SELECT
    orderId,
    'COMPLETED' AS status,
    CAST(NULL AS VARCHAR) AS userId,
    CAST(NULL AS DOUBLE) AS total,
    CAST(NULL AS ARRAY<STRUCT<sku VARCHAR, quantity INT, price DOUBLE>>) AS items,
    timestamp,
    'Order completed successfully' AS details
FROM order_completed_stream
EMIT CHANGES;

-- Add order cancelled events
INSERT INTO order_events
SELECT
    orderId,
    'CANCELLED' AS status,
    CAST(NULL AS VARCHAR) AS userId,
    CAST(NULL AS DOUBLE) AS total,
    CAST(NULL AS ARRAY<STRUCT<sku VARCHAR, quantity INT, price DOUBLE>>) AS items,
    timestamp,
    CONCAT('Order cancelled: ', reason) AS details
FROM order_cancelled_stream
EMIT CHANGES;

-- =============================================================================
-- ORDER STATUS TABLE - Latest Status per Order
-- =============================================================================

-- Create a table with the latest status per order
CREATE TABLE order_status AS
SELECT
    orderId,
    LATEST_BY_OFFSET(status) AS currentStatus,
    LATEST_BY_OFFSET(userId) AS userId,
    LATEST_BY_OFFSET(total) AS orderTotal,
    LATEST_BY_OFFSET(items) AS orderItems,
    LATEST_BY_OFFSET(timestamp) AS lastUpdated,
    LATEST_BY_OFFSET(details) AS lastStatusDetails,
    COUNT(*) AS totalStatusChanges
FROM order_events
GROUP BY orderId
EMIT CHANGES;

-- =============================================================================
-- EXAMPLE QUERIES
-- =============================================================================

-- PUSH QUERIES (Continuous queries that stream results)
--
-- 1. Monitor all order status changes in real-time:
-- SELECT * FROM order_events EMIT CHANGES;
--
-- 2. Watch for failed orders:
-- SELECT * FROM order_events
-- WHERE status IN ('PAYMENT_FAILED', 'INVENTORY_REJECTED', 'CANCELLED')
-- EMIT CHANGES;
--
-- 3. Monitor orders stuck in specific states:
-- SELECT orderId, currentStatus, lastUpdated
-- FROM order_status
-- WHERE currentStatus = 'CREATED'
-- AND lastUpdated < (UNIX_TIMESTAMP() - 300000)
-- EMIT CHANGES;

-- PULL QUERIES (One-time queries for current state)
--
-- 4. Get current status of a specific order:
-- SELECT * FROM order_status WHERE orderId = 'order-123';
--
-- 5. Get all orders with specific status:
-- SELECT * FROM order_status WHERE currentStatus = 'PAYMENT_AUTHORIZED';
--
-- 6. Get order status summary:
-- SELECT currentStatus, COUNT(*) as count
-- FROM order_status
-- GROUP BY currentStatus;
--
-- 7. Get orders that have had many status changes:
-- SELECT * FROM order_status WHERE totalStatusChanges > 5;

-- =============================================================================
-- OPERATIONAL QUERIES
-- =============================================================================

-- Create a stream for monitoring problematic orders
CREATE STREAM problematic_orders AS
SELECT
    orderId,
    currentStatus,
    lastUpdated,
    totalStatusChanges,
    'High status changes detected' AS alert_reason
FROM order_status
WHERE totalStatusChanges > 10
EMIT CHANGES;

-- Create alerts for orders stuck in payment
CREATE STREAM payment_stuck_alerts AS
SELECT
    orderId,
    currentStatus,
    lastUpdated,
    (UNIX_TIMESTAMP() - lastUpdated) / 1000 AS seconds_stuck
FROM order_status
WHERE currentStatus = 'CREATED'
AND (UNIX_TIMESTAMP() - lastUpdated) > 300000  -- 5 minutes
EMIT CHANGES;

-- =============================================================================
-- USEFUL COMMANDS FOR MANAGEMENT
-- =============================================================================

-- List all streams and tables:
-- SHOW STREAMS;
-- SHOW TABLES;

-- Describe a specific stream or table:
-- DESCRIBE order_events;
-- DESCRIBE order_status;

-- Show running queries:
-- SHOW QUERIES;

-- Terminate a query:
-- TERMINATE query_id;

-- Drop streams/tables (be careful!):
-- DROP STREAM order_events DELETE TOPIC;
-- DROP TABLE order_status DELETE TOPIC;
