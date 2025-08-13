-- Create outbox events table for transactional outbox pattern
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    headers JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE
);

-- Create index on processed_at for efficient polling of unprocessed events
CREATE INDEX idx_outbox_events_processed_at ON outbox_events(processed_at);

-- Create index on created_at for ordering
CREATE INDEX idx_outbox_events_created_at ON outbox_events(created_at);

-- Create composite index for efficient querying of unprocessed events
CREATE INDEX idx_outbox_events_unprocessed ON outbox_events(processed_at, created_at)
WHERE processed_at IS NULL;

-- Create index on aggregate for tracking events by aggregate
CREATE INDEX idx_outbox_events_aggregate ON outbox_events(aggregate_type, aggregate_id);
