-- Prompt 5: persist the commercial percentage chosen for each manual Discogs batch.
-- Historical finalized batches intentionally remain NULL; there is no backfill.
ALTER TABLE discogs_manual_batch
    ADD COLUMN IF NOT EXISTS porcentaje_sonograma INTEGER;
