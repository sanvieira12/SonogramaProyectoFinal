ALTER TABLE discogs_import_row
    ADD COLUMN IF NOT EXISTS observation TEXT;
