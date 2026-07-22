ALTER TABLE ledger_entries ADD COLUMN posted_at TIMESTAMPTZ;

UPDATE ledger_entries e
SET posted_at = t.posted_at
FROM ledger_transactions t
WHERE e.ledger_transaction_id = t.id;

ALTER TABLE ledger_entries ALTER COLUMN posted_at SET NOT NULL;

DROP INDEX ledger_entries_account_id_idx;

CREATE INDEX ledger_entries_account_posted_idx
    ON ledger_entries (account_id, posted_at, ledger_transaction_id, entry_sequence);
