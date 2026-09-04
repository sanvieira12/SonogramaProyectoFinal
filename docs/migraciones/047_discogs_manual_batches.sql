-- Discogs Phase 1: persistent business batches for the manual customer workflow.
-- This migration is additive and deliberately does not alter historical
-- Discogs import jobs/rows or technical manual import operations.
CREATE TABLE IF NOT EXISTS discogs_manual_batch (
    id_discogs_manual_batch BIGSERIAL PRIMARY KEY,
    customer_code           VARCHAR(255) NOT NULL,
    normalized_customer_code VARCHAR(255) NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    started_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finalized_at            TIMESTAMP NULL,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Only one live batch may exist for a normalized customer code. Finalized
-- batches remain historical and may share the same customer code.
CREATE UNIQUE INDEX IF NOT EXISTS uk_discogs_manual_batch_open_customer
    ON discogs_manual_batch (normalized_customer_code)
    WHERE status = 'OPEN';

ALTER TABLE disco_qr_copy
    ADD COLUMN IF NOT EXISTS id_discogs_manual_batch BIGINT
        REFERENCES discogs_manual_batch(id_discogs_manual_batch);

ALTER TABLE disco_qr_copy
    ADD COLUMN IF NOT EXISTS precio_venta NUMERIC(14,6);

ALTER TABLE disco_qr_copy
    ADD COLUMN IF NOT EXISTS condicion_fisica TEXT;

CREATE INDEX IF NOT EXISTS idx_disco_qr_copy_manual_batch
    ON disco_qr_copy (id_discogs_manual_batch)
    WHERE id_discogs_manual_batch IS NOT NULL;
