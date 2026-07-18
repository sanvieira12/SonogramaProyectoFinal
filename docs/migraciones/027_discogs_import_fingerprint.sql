ALTER TABLE discogs_import_job
    ADD COLUMN IF NOT EXISTS source_fingerprint VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_discogs_import_job_source_fingerprint
    ON discogs_import_job(source_fingerprint);
