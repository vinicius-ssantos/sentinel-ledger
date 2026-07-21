CREATE TABLE idempotency_records (
    merchant_id UUID NOT NULL,
    operation_name VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    state VARCHAR(20) NOT NULL,
    resource_id VARCHAR(255),
    response_status SMALLINT,
    response_content_type VARCHAR(64),
    response_body TEXT,
    response_location VARCHAR(2048),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (merchant_id, operation_name, idempotency_key),
    CONSTRAINT idempotency_records_state_check
        CHECK (state IN ('IN_PROGRESS', 'COMPLETED', 'RECOVERY_REQUIRED', 'FAILED_TERMINAL')),
    CONSTRAINT idempotency_records_terminal_response_check
        CHECK (state IN ('IN_PROGRESS', 'RECOVERY_REQUIRED') OR response_status IS NOT NULL),
    CONSTRAINT idempotency_records_recovery_resource_check
        CHECK (state <> 'RECOVERY_REQUIRED' OR resource_id IS NOT NULL),
    CONSTRAINT idempotency_records_updated_at_check
        CHECK (updated_at >= created_at)
);
