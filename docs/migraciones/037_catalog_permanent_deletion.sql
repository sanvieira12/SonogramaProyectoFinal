-- Permanent Catalog deletion marker.
-- Rows without business history are physically deleted by the application.
-- Rows referenced by sales/audit history remain as tombstones and are excluded
-- from every normal Catalog and import lookup.
ALTER TABLE disco
    ADD COLUMN IF NOT EXISTS catalog_deleted_at TIMESTAMP NULL;

ALTER TABLE disco
    ADD COLUMN IF NOT EXISTS catalog_deleted_by VARCHAR(255) NULL;

CREATE INDEX IF NOT EXISTS idx_disco_catalog_active
    ON disco (id_disco)
    WHERE catalog_deleted_at IS NULL;
