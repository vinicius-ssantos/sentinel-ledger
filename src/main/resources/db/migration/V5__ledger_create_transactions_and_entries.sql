CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY,
    business_effect_reference VARCHAR(255) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    currency_fraction_digits SMALLINT NOT NULL,
    reverses_ledger_transaction_id UUID REFERENCES ledger_transactions (id),
    posted_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ledger_transactions_business_effect_reference_key UNIQUE (business_effect_reference),
    CONSTRAINT ledger_transactions_currency_fraction_digits_check
        CHECK (currency_fraction_digits BETWEEN 0 AND 3)
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    ledger_transaction_id UUID NOT NULL REFERENCES ledger_transactions (id),
    entry_sequence SMALLINT NOT NULL,
    account_id VARCHAR(255) NOT NULL,
    direction VARCHAR(6) NOT NULL,
    amount_minor BIGINT NOT NULL,
    CONSTRAINT ledger_entries_direction_check CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ledger_entries_amount_positive_check CHECK (amount_minor > 0),
    CONSTRAINT ledger_entries_unique_sequence UNIQUE (ledger_transaction_id, entry_sequence)
);

CREATE INDEX ledger_entries_ledger_transaction_id_idx
    ON ledger_entries (ledger_transaction_id);

CREATE INDEX ledger_entries_account_id_idx
    ON ledger_entries (account_id);

CREATE FUNCTION reject_ledger_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'ledger records are append-only and cannot be updated or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_transactions_append_only
    BEFORE UPDATE OR DELETE ON ledger_transactions
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();

CREATE TRIGGER ledger_entries_append_only
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();

CREATE TABLE ledger_account_balance_projections (
    account_id VARCHAR(255) PRIMARY KEY,
    currency_code CHAR(3) NOT NULL,
    currency_fraction_digits SMALLINT NOT NULL,
    debit_total_minor BIGINT NOT NULL DEFAULT 0,
    credit_total_minor BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ledger_account_balance_projections_non_negative_check
        CHECK (debit_total_minor >= 0 AND credit_total_minor >= 0),
    CONSTRAINT ledger_account_balance_projections_currency_fraction_digits_check
        CHECK (currency_fraction_digits BETWEEN 0 AND 3)
);
