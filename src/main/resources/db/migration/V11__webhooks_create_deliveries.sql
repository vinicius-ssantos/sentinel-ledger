CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    delivered_at TIMESTAMPTZ,
    CONSTRAINT webhook_deliveries_status_check CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED'))
);

CREATE INDEX webhook_deliveries_aggregate_idx
    ON webhook_deliveries (aggregate_type, aggregate_id, created_at);
