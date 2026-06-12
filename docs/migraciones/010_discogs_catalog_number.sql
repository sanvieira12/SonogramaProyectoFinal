ALTER TABLE discogs_import_row
    ADD COLUMN IF NOT EXISTS catalog_number VARCHAR(255);
