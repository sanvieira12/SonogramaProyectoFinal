-- Phase 6: source-wide expected counts and immutable technical-batch snapshots.
-- No historical source receives an inferred expected count or snapshot.
CREATE TABLE IF NOT EXISTS manual_discogs_source_reconciliation (
    id BIGSERIAL PRIMARY KEY,
    source_customer_code VARCHAR(255) NOT NULL,
    normalized_source_customer_code VARCHAR(255) NOT NULL,
    expected_copy_count INTEGER NULL,
    note TEXT NULL,
    expected_count_change_reason TEXT NULL,
    updated_by VARCHAR(255) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_manual_discogs_reconciliation_source
        UNIQUE (normalized_source_customer_code),
    CONSTRAINT ck_manual_discogs_expected_count
        CHECK (expected_copy_count IS NULL OR (expected_copy_count >= 0 AND expected_copy_count <= 1000000))
);

CREATE TABLE IF NOT EXISTS manual_discogs_finalization_snapshot (
    id BIGSERIAL PRIMARY KEY,
    id_discogs_manual_batch BIGINT NOT NULL
        REFERENCES discogs_manual_batch(id_discogs_manual_batch),
    source_customer_code VARCHAR(255) NOT NULL,
    normalized_source_customer_code VARCHAR(255) NOT NULL,
    expected_copy_count INTEGER NULL,
    provable_physical_copy_count BIGINT NOT NULL,
    available_copy_count BIGINT NOT NULL,
    sold_copy_count BIGINT NOT NULL,
    removed_copy_count BIGINT NOT NULL,
    distinct_release_count BIGINT NOT NULL,
    duplicate_release_group_count BIGINT NOT NULL,
    extra_duplicate_copy_count BIGINT NOT NULL,
    pending_operation_count BIGINT NOT NULL,
    completed_operation_count BIGINT NOT NULL,
    abandoned_operation_count BIGINT NOT NULL,
    difference BIGINT NULL,
    reconciliation_status VARCHAR(40) NOT NULL,
    finalized_at TIMESTAMP NOT NULL,
    finalized_by VARCHAR(255) NULL,
    CONSTRAINT uk_manual_discogs_snapshot_batch UNIQUE (id_discogs_manual_batch)
);

CREATE INDEX IF NOT EXISTS idx_manual_discogs_snapshot_source_finalized
    ON manual_discogs_finalization_snapshot (normalized_source_customer_code, finalized_at);

-- Deliberately no INSERT/UPDATE/backfill. Existing sources remain unknown.
