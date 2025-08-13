CREATE TABLE processed_orders (
    order_id VARCHAR(255) PRIMARY KEY,
    status VARCHAR(50) NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    retry_attempts INTEGER DEFAULT 0
);

CREATE INDEX idx_processed_orders_status ON processed_orders(status);
CREATE INDEX idx_processed_orders_processed_at ON processed_orders(processed_at);
