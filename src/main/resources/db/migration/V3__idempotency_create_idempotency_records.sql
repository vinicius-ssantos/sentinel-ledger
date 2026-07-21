CREATE TABLE idempotency_records (
    merchant_id UUID NOT NULL,
    operation_name VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    state VARCHAR(20) NOT NULL,
    response_status SMALLINT,
    response_content_type VARCHAR(64),
    response_body TEXT,
    response_location VARCHAR(2048),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (merchant_id, operation_name, idempotency_key),
    CHECK (state IN ('IN_PROGRESS', 'COMPLETED', 'RECOVERY_REQUIRED', 'FAILED_TERMINAL')),
    CHECK (state = 'IN_PROGRESS' OR response_status IS NOT NULL),
    CHECK (updated_at >= created_at)
);
