-- Estados independientes y progreso persistente del flujo Excel Discogs.
ALTER TABLE discogs_import_job
    ADD COLUMN IF NOT EXISTS stage VARCHAR(50) NOT NULL DEFAULT 'PARSING_ROWS',
    ADD COLUMN IF NOT EXISTS zip_status VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN IF NOT EXISTS zip_total_covers INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS zip_processed_covers INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS zip_added_covers INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS zip_failed_covers INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS zip_current_release VARCHAR(500),
    ADD COLUMN IF NOT EXISTS zip_file_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS zip_error TEXT;

ALTER TABLE discogs_import_row
    ADD COLUMN IF NOT EXISTS metadata_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS metadata_error_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS cover_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS cover_error_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS cover_local_path TEXT,
    ADD COLUMN IF NOT EXISTS youtube_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS youtube_error_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS youtube_tracks_found INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS youtube_tracks_missing INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS catalog_import_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS catalog_import_error_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS warning_message TEXT;

ALTER TABLE discogs_import_row
    ALTER COLUMN url_source TYPE VARCHAR(40);

-- Cada backfill es monotónico para que volver a ejecutar este script no cambie
-- una etapa activa o un resultado más preciso escrito por la aplicación.
UPDATE discogs_import_job
SET stage = 'COMPLETED'
WHERE stage = 'PARSING_ROWS'
  AND status IN ('COMPLETED', 'COMPLETED_WITH_WARNINGS', 'COMPLETED_WITH_ERRORS', 'FAILED');

UPDATE discogs_import_row
SET metadata_status = 'SUCCESS'
WHERE metadata_status = 'PENDING'
  AND resolved_release_id IS NOT NULL;

UPDATE discogs_import_row
SET metadata_status = 'MISSING_LINK'
WHERE metadata_status = 'PENDING'
  AND discogs_id IS NULL
  AND status IN ('NEEDS_MANUAL_MATCH', 'IGNORED');

UPDATE discogs_import_row
SET metadata_status = 'RATE_LIMITED'
WHERE metadata_status = 'PENDING'
  AND status IN ('RATE_LIMITED', 'PENDING_RETRY');

UPDATE discogs_import_row
SET metadata_status = 'FAILED'
WHERE metadata_status = 'PENDING'
  AND status = 'FAILED';

UPDATE discogs_import_row
SET cover_status = 'SUCCESS'
WHERE cover_status = 'PENDING'
  AND image_url LIKE '%/discogs/covers/%';

UPDATE discogs_import_row
SET cover_status = 'UNAVAILABLE'
WHERE cover_status = 'PENDING'
  AND metadata_status = 'SUCCESS'
  AND (image_url IS NULL OR image_url NOT LIKE '%/discogs/covers/%');

UPDATE discogs_import_row
SET catalog_import_status = 'IMPORTED'
WHERE catalog_import_status = 'PENDING'
  AND imported_catalog_product_id IS NOT NULL;

UPDATE discogs_import_row
SET catalog_import_status = 'SKIPPED_SOLD'
WHERE catalog_import_status = 'PENDING'
  AND source_status = 'VENDIDO';

UPDATE discogs_import_row
SET catalog_import_status = 'SKIPPED_RESERVED'
WHERE catalog_import_status = 'PENDING'
  AND source_status = 'RESERVADO';

UPDATE discogs_import_row
SET catalog_import_status = 'MANUAL_REVIEW'
WHERE catalog_import_status = 'PENDING'
  AND metadata_status IN ('MISSING_LINK', 'FAILED');

UPDATE discogs_import_row
SET catalog_import_status = 'READY'
WHERE catalog_import_status = 'PENDING'
  AND metadata_status = 'SUCCESS'
  AND COALESCE(source_status, 'DISPONIBLE') NOT IN ('VENDIDO', 'RESERVADO');
