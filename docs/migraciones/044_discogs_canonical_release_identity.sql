-- Discogs Phase 2: nullable concrete-release catalogue identity.
-- No UNIQUE constraint is introduced: production legacy data has not yet been
-- reconciled and duplicate historical rows must remain reviewable.
ALTER TABLE disco
    ADD COLUMN IF NOT EXISTS discogs_release_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_disco_discogs_release_id
    ON disco(discogs_release_id)
    WHERE discogs_release_id IS NOT NULL;

-- Backfill only a concrete release URL which occurs on exactly one active
-- catalogue row. Master URLs and ambiguous duplicate release identities remain
-- NULL intentionally. This accepts the URL forms already supported by the
-- Discogs parser: /release/, /es/release/ and /sell/release/.
WITH candidates AS (
    SELECT d.id_disco,
           ((regexp_match(lower(d.discogs_url),
              '/(?:[a-z]{2}/)?(?:sell/)?release/([0-9]+)'))[1])::BIGINT AS release_id
    FROM disco d
    WHERE d.catalog_deleted_at IS NULL
      AND d.discogs_release_id IS NULL
      AND d.discogs_url IS NOT NULL
), unique_candidates AS (
    SELECT release_id
    FROM candidates
    WHERE release_id IS NOT NULL
    GROUP BY release_id
    HAVING COUNT(*) = 1
)
UPDATE disco d
SET discogs_release_id = c.release_id
FROM candidates c
JOIN unique_candidates u ON u.release_id = c.release_id
WHERE d.id_disco = c.id_disco
  AND d.discogs_release_id IS NULL;

-- Report, without changing, historical concrete-release collisions for later
-- reconciliation. The deployment runner may print this result safely.
SELECT discogs_release_id, ARRAY_AGG(id_disco ORDER BY id_disco) AS discos
FROM disco
WHERE catalog_deleted_at IS NULL
  AND discogs_release_id IS NOT NULL
GROUP BY discogs_release_id
HAVING COUNT(*) > 1;
