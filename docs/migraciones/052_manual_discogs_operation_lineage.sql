-- Phase 5: durable manual Discogs operation lineage and pending audit context.
-- All operation columns are nullable so historical rows remain unknown rather
-- than receiving inferred source, batch, copy, price, condition or override data.
ALTER TABLE manual_discogs_import_operation
    ADD COLUMN IF NOT EXISTS id_discogs_manual_batch BIGINT
        REFERENCES discogs_manual_batch(id_discogs_manual_batch);

ALTER TABLE manual_discogs_import_operation
    ADD COLUMN IF NOT EXISTS source_customer_code VARCHAR(255);

ALTER TABLE manual_discogs_import_operation
    ADD COLUMN IF NOT EXISTS normalized_source_customer_code VARCHAR(255);

ALTER TABLE manual_discogs_import_operation
    ADD COLUMN IF NOT EXISTS submitted_price NUMERIC(14,6);

ALTER TABLE manual_discogs_import_operation
    ADD COLUMN IF NOT EXISTS submitted_condition TEXT;

ALTER TABLE manual_discogs_import_operation
    ADD COLUMN IF NOT EXISTS duplicate_override BOOLEAN;

ALTER TABLE manual_discogs_import_operation
    ADD COLUMN IF NOT EXISTS duplicate_override_reason TEXT;

ALTER TABLE manual_discogs_import_operation
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;

ALTER TABLE manual_discogs_import_operation
    ADD COLUMN IF NOT EXISTS abandoned_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS manual_discogs_import_operation_copy (
    operation_id UUID NOT NULL
        REFERENCES manual_discogs_import_operation(operation_id),
    copy_id BIGINT NOT NULL
        REFERENCES disco_qr_copy(id),
    PRIMARY KEY (operation_id, copy_id),
    CONSTRAINT uk_manual_discogs_operation_copy_copy UNIQUE (copy_id)
);

CREATE INDEX IF NOT EXISTS idx_manual_discogs_operation_source_status
    ON manual_discogs_import_operation (normalized_source_customer_code, status, created_at);

CREATE INDEX IF NOT EXISTS idx_discogs_manual_batch_normalized_source
    ON discogs_manual_batch (normalized_customer_code);

CREATE INDEX IF NOT EXISTS idx_manual_discogs_operation_batch
    ON manual_discogs_import_operation (id_discogs_manual_batch);

CREATE INDEX IF NOT EXISTS idx_manual_discogs_operation_copy_operation
    ON manual_discogs_import_operation_copy (operation_id);

-- Deliberately no UPDATE/backfill: old rows keep unknown lineage as NULL.
