-- Create orders table
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id VARCHAR(255) NOT NULL UNIQUE,
    user_id VARCHAR(255) NOT NULL,
    total DECIMAL(10,2) NOT NULL,
    items JSONB NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('NEW', 'PAID', 'RESERVED', 'SHIPPED', 'COMPLETED', 'CANCELLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index on order_id for fast lookups
CREATE INDEX idx_orders_order_id ON orders(order_id);

-- Create index on user_id for user queries
CREATE INDEX idx_orders_user_id ON orders(user_id);

-- Create index on status for status-based queries
CREATE INDEX idx_orders_status ON orders(status);

-- Create index on created_at for time-based queries
CREATE INDEX idx_orders_created_at ON orders(created_at);

-- Create trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_orders_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
