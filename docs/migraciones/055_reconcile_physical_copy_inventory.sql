BEGIN;

-- Physical copies are the inventory authority. This reconciliation is safe to
-- replay: it fills missing AVAILABLE USED copy data, makes an unambiguous
-- single available copy authoritative for the compatibility fields on disco,
-- and derives aggregate stock/state from retained physical rows.

UPDATE disco_qr_copy c
SET precio_venta = COALESCE(c.precio_venta, d.precio_venta),
    condicion_fisica = COALESCE(
        NULLIF(BTRIM(c.condicion_fisica), ''),
        LEFT(UPPER(NULLIF(BTRIM(d.condicion_fisica), '')), 50)
    ),
    updated_at = CASE
        WHEN c.precio_venta IS NULL AND d.precio_venta IS NOT NULL THEN CURRENT_TIMESTAMP
        WHEN (c.condicion_fisica IS NULL OR BTRIM(c.condicion_fisica) = '')
             AND d.condicion_fisica IS NOT NULL
             AND BTRIM(d.condicion_fisica) <> '' THEN CURRENT_TIMESTAMP
        ELSE c.updated_at
    END
FROM disco d
WHERE c.id_disco = d.id_disco
  AND d.catalog_deleted_at IS NULL
  AND d.condicion = 'USADO'
  AND c.estado = 'DISPONIBLE'
  AND (
      (c.precio_venta IS NULL AND d.precio_venta IS NOT NULL)
      OR ((c.condicion_fisica IS NULL OR BTRIM(c.condicion_fisica) = '')
          AND d.condicion_fisica IS NOT NULL
          AND BTRIM(d.condicion_fisica) <> '')
  );

WITH single_available AS (
    SELECT c.id_disco,
           MAX(c.id) AS copy_id
    FROM disco_qr_copy c
    JOIN disco d ON d.id_disco = c.id_disco
    WHERE d.catalog_deleted_at IS NULL
      AND d.condicion = 'USADO'
      AND c.estado = 'DISPONIBLE'
    GROUP BY c.id_disco
    HAVING COUNT(*) = 1
)
UPDATE disco d
SET precio_venta = COALESCE(c.precio_venta, d.precio_venta),
    condicion_fisica = COALESCE(
        NULLIF(LEFT(UPPER(BTRIM(c.condicion_fisica)), 50), ''),
        d.condicion_fisica
    ),
    pricing_mode = CASE
        WHEN c.precio_venta IS NOT NULL THEN 'MANUAL'
        ELSE d.pricing_mode
    END
FROM single_available only_copy
JOIN disco_qr_copy c ON c.id = only_copy.copy_id
WHERE d.id_disco = only_copy.id_disco;

WITH inventory AS (
    SELECT c.id_disco,
           COUNT(*) FILTER (WHERE c.estado = 'DISPONIBLE') AS available_count,
           COUNT(*) FILTER (WHERE c.estado = 'VENDIDO') AS sold_count,
           (ARRAY_AGG(c.codigo_qr ORDER BY
               CASE WHEN c.estado = 'DISPONIBLE' THEN 0 ELSE 1 END,
               c.copy_number,
               c.id))[1] AS preferred_qr
    FROM disco_qr_copy c
    JOIN disco d ON d.id_disco = c.id_disco
    WHERE d.catalog_deleted_at IS NULL
    GROUP BY c.id_disco
)
UPDATE disco d
SET cantidad_copias = inventory.available_count,
    estado = CASE
        WHEN d.estado = 'RESERVADO' AND inventory.available_count > 0 THEN 'RESERVADO'
        WHEN inventory.available_count > 0 THEN 'DISPONIBLE'
        WHEN inventory.sold_count > 0 THEN 'VENDIDO'
        ELSE 'SIN_STOCK'
    END,
    codigo_qr = inventory.preferred_qr
FROM inventory
WHERE d.id_disco = inventory.id_disco
  AND (
      d.cantidad_copias IS DISTINCT FROM inventory.available_count
      OR d.estado IS DISTINCT FROM CASE
          WHEN d.estado = 'RESERVADO' AND inventory.available_count > 0 THEN 'RESERVADO'
          WHEN inventory.available_count > 0 THEN 'DISPONIBLE'
          WHEN inventory.sold_count > 0 THEN 'VENDIDO'
          ELSE 'SIN_STOCK'
      END
      OR d.codigo_qr IS DISTINCT FROM inventory.preferred_qr
  );

COMMIT;
