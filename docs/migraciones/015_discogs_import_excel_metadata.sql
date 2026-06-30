ALTER TABLE discogs_import_job
    ADD COLUMN IF NOT EXISTS physical_excel_last_row INTEGER,
    ADD COLUMN IF NOT EXISTS ignored_blank_rows INTEGER NOT NULL DEFAULT 0;

ALTER TABLE discogs_import_row
    ADD COLUMN IF NOT EXISTS raw_condition VARCHAR(255),
    ADD COLUMN IF NOT EXISTS manual_condition VARCHAR(255),
    ADD COLUMN IF NOT EXISTS raw_price VARCHAR(255),
    ADD COLUMN IF NOT EXISTS manual_price_uyu NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS manual_genre VARCHAR(255),
    ADD COLUMN IF NOT EXISTS source_status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS internal_code VARCHAR(255);

UPDATE discogs_import_job
SET ignored_blank_rows = 0
WHERE ignored_blank_rows IS NULL;

UPDATE discogs_import_row
SET source_status = 'DISPONIBLE'
WHERE source_status IS NULL;
