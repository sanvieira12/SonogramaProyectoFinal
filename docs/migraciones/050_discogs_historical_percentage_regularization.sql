-- Prompt 7B: one-time historical NULL backfill for client-confirmed batches.
-- This is deliberately not a runtime customer default. New/open batches remain
-- NULL until the normal Prompt 5 finalization workflow receives an explicit value.
UPDATE discogs_manual_batch
SET porcentaje_sonograma = CASE normalized_customer_code
    WHEN 'F'   THEN 35
    WHEN 'P'   THEN 20
    WHEN 'FP'  THEN 30
    WHEN 'LVS' THEN 35
    WHEN 'SC'  THEN 25
    WHEN 'JPH' THEN 25
    WHEN 'JS'  THEN 30
    ELSE porcentaje_sonograma
END
WHERE status = 'FINALIZED'
  AND porcentaje_sonograma IS NULL
  AND normalized_customer_code IN ('F', 'P', 'FP', 'LVS', 'SC', 'JPH', 'JS');
