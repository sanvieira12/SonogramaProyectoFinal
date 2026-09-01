-- Vinyl Future Phase 2: supplier identity and invoice-operation idempotency.
-- The new columns are nullable so pre-existing ambiguous rows are never merged or deleted.
ALTER TABLE disco
    ADD COLUMN IF NOT EXISTS vinylfuture_supplier_code_normalized VARCHAR(180);

ALTER TABLE pedido
    ADD COLUMN IF NOT EXISTS vinylfuture_operation_key VARCHAR(240);

-- Backfill only supplier codes that identify exactly one active Future product.
-- Meaningful punctuation is preserved; only Unicode-independent SQL-safe formatting
-- differences (case and repeated whitespace) are normalized here.
WITH candidates AS (
    SELECT
        d.id_disco,
        UPPER(REGEXP_REPLACE(TRIM(d.codigo_interno), '[[:space:]]+', ' ', 'g')) AS identity,
        COUNT(*) OVER (
            PARTITION BY UPPER(REGEXP_REPLACE(TRIM(d.codigo_interno), '[[:space:]]+', ' ', 'g'))
        ) AS identity_count
    FROM disco d
    WHERE d.catalog_deleted_at IS NULL
      AND d.codigo_interno IS NOT NULL
      AND TRIM(d.codigo_interno) <> ''
      AND UPPER(REGEXP_REPLACE(COALESCE(d.procedencia, ''), '[^A-Z0-9]+', '', 'g'))
          IN ('FUTURE', 'VINYLFUTURE', 'DEEJAYDE')
)
UPDATE disco d
SET vinylfuture_supplier_code_normalized = candidates.identity
FROM candidates
WHERE d.id_disco = candidates.id_disco
  AND candidates.identity_count = 1
  AND d.vinylfuture_supplier_code_normalized IS NULL;

-- Duplicate legacy identities intentionally remain NULL. This query reports them
-- without modifying data and can be run before/after the migration for review.
SELECT
    UPPER(REGEXP_REPLACE(TRIM(codigo_interno), '[[:space:]]+', ' ', 'g')) AS identidad,
    ARRAY_AGG(id_disco ORDER BY id_disco) AS discos
FROM disco
WHERE catalog_deleted_at IS NULL
  AND codigo_interno IS NOT NULL
  AND TRIM(codigo_interno) <> ''
  AND UPPER(REGEXP_REPLACE(COALESCE(procedencia, ''), '[^A-Z0-9]+', '', 'g'))
      IN ('FUTURE', 'VINYLFUTURE', 'DEEJAYDE')
GROUP BY UPPER(REGEXP_REPLACE(TRIM(codigo_interno), '[[:space:]]+', ' ', 'g'))
HAVING COUNT(*) > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_disco_vinylfuture_supplier_identity
    ON disco(vinylfuture_supplier_code_normalized)
    WHERE vinylfuture_supplier_code_normalized IS NOT NULL;

-- Link Phase 1 parsed rows only when invoice number and normalized supplier
-- identity both point to one deterministic active Future product.
WITH deterministic_links AS (
    SELECT pi.id_pedido_item, MIN(d.id_disco) AS id_disco
    FROM pedido_item pi
    JOIN pedido p ON p.id_pedido = pi.id_pedido
    JOIN disco d
      ON d.catalog_deleted_at IS NULL
     AND d.vinylfuture_supplier_code_normalized IS NOT NULL
     AND d.vinylfuture_supplier_code_normalized =
         UPPER(REGEXP_REPLACE(TRIM(pi.codigo), '[[:space:]]+', ' ', 'g'))
     AND UPPER(REGEXP_REPLACE(TRIM(d.numero_factura_compra), '[[:space:]]+', ' ', 'g')) =
         UPPER(REGEXP_REPLACE(TRIM(p.numero_factura), '[[:space:]]+', ' ', 'g'))
    WHERE LOWER(COALESCE(p.origen_importacion, '')) = 'vinylfuture'
      AND pi.id_disco IS NULL
      AND COALESCE(pi.estado_lectura, 'PARSED') <> 'REVIEW_REQUIRED'
      AND pi.codigo IS NOT NULL
      AND TRIM(pi.codigo) <> ''
    GROUP BY pi.id_pedido_item
    HAVING COUNT(DISTINCT d.id_disco) = 1
)
UPDATE pedido_item pi
SET id_disco = deterministic_links.id_disco,
    enrich_status = 'IMPORTED',
    estado_lectura = CASE
        WHEN COALESCE(pi.estado_lectura, 'PARSED') = 'REVIEW_REQUIRED' THEN pi.estado_lectura
        ELSE 'IMPORTADO'
    END
FROM deterministic_links
WHERE pi.id_pedido_item = deterministic_links.id_pedido_item;

-- A historical invoice is complete only when it has rows and every row is linked.
UPDATE pedido p
SET import_status = 'COMPLETED',
    updated_at = CURRENT_TIMESTAMP
WHERE LOWER(COALESCE(p.origen_importacion, '')) = 'vinylfuture'
  AND EXISTS (
      SELECT 1 FROM pedido_item pi WHERE pi.id_pedido = p.id_pedido
  )
  AND NOT EXISTS (
      SELECT 1 FROM pedido_item pi
      WHERE pi.id_pedido = p.id_pedido AND pi.id_disco IS NULL
  );

-- Backfill an operation key only where the historical invoice identity is unique.
WITH invoice_candidates AS (
    SELECT
        p.id_pedido,
        'VINYLFUTURE:' || UPPER(REGEXP_REPLACE(TRIM(p.numero_factura), '[[:space:]]+', ' ', 'g')) AS operation_key,
        COUNT(*) OVER (
            PARTITION BY UPPER(REGEXP_REPLACE(TRIM(p.numero_factura), '[[:space:]]+', ' ', 'g'))
        ) AS invoice_count
    FROM pedido p
    WHERE LOWER(COALESCE(p.origen_importacion, '')) = 'vinylfuture'
      AND p.numero_factura IS NOT NULL
      AND TRIM(p.numero_factura) <> ''
)
UPDATE pedido p
SET vinylfuture_operation_key = invoice_candidates.operation_key
FROM invoice_candidates
WHERE p.id_pedido = invoice_candidates.id_pedido
  AND invoice_candidates.invoice_count = 1
  AND p.vinylfuture_operation_key IS NULL;

-- Report historical duplicate invoice identities without deleting or merging them.
SELECT
    UPPER(REGEXP_REPLACE(TRIM(numero_factura), '[[:space:]]+', ' ', 'g')) AS factura,
    ARRAY_AGG(id_pedido ORDER BY id_pedido) AS pedidos
FROM pedido
WHERE LOWER(COALESCE(origen_importacion, '')) = 'vinylfuture'
  AND numero_factura IS NOT NULL
  AND TRIM(numero_factura) <> ''
GROUP BY UPPER(REGEXP_REPLACE(TRIM(numero_factura), '[[:space:]]+', ' ', 'g'))
HAVING COUNT(*) > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_pedido_vinylfuture_operation_key
    ON pedido(vinylfuture_operation_key)
    WHERE vinylfuture_operation_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pedido_item_vinylfuture_pending
    ON pedido_item(estado_lectura, id_pedido)
    WHERE estado_lectura = 'REVIEW_REQUIRED' AND id_disco IS NULL;
