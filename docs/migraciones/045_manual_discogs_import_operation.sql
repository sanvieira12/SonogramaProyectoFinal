-- Discogs Phase 3: durable idempotency key for one manually confirmed receipt.
-- This is intentionally independent from the catalogue release identity: a new
-- preview gets a new operation and may legitimately add another physical copy.
CREATE TABLE IF NOT EXISTS manual_discogs_import_operation (
    operation_id UUID PRIMARY KEY,
    discogs_release_id BIGINT NOT NULL,
    requested_copies INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    resulting_product_id BIGINT NULL,
    result_type VARCHAR(40) NULL,
    available_copies INTEGER NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_manual_discogs_import_operation_release
    ON manual_discogs_import_operation (discogs_release_id);
