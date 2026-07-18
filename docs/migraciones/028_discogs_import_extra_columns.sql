ALTER TABLE discogs_import_job
    ADD COLUMN IF NOT EXISTS extra_columns TEXT;
