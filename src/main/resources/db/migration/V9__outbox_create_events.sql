CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    claimed_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    CONSTRAINT outbox_events_status_check CHECK (status IN ('PENDING', 'CLAIMED', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX outbox_events_status_created_at_idx
    ON outbox_events (status, created_at);

CREATE INDEX outbox_events_aggregate_idx
    ON outbox_events (aggregate_type, aggregate_id);
