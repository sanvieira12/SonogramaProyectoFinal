-- Reconstruct historical VinylFuture order headers/items from catalog rows.
-- The original PDFs are not available for these imports, so these orders are
-- explicitly marked partial and use the persisted catalog quantities.
-- Migration 024 may already have made linea_factura mandatory on a database
-- where this backfill is being replayed. Keep this migration compatible with
-- both orderings while retaining its idempotent item guard below.
ALTER TABLE pedido_item ADD COLUMN IF NOT EXISTS linea_factura INTEGER;

INSERT INTO pedido (
    numero_factura, fecha_factura, proveedor, origen_importacion,
    moneda, nombre_archivo, cantidad_total_pdf, neto, total,
    import_status, created_at, updated_at
)
SELECT
    d.numero_factura_compra,
    MIN(d.fecha_factura_compra),
    'VinylFuture',
    'vinylfuture',
    'EUR',
    'backfill-catalogo-' || d.numero_factura_compra || '.pdf',
    SUM(COALESCE(d.cantidad_copias, 1)),
    SUM(COALESCE(d.costo, 0) * COALESCE(d.cantidad_copias, 1)),
    SUM(COALESCE(d.costo, 0) * COALESCE(d.cantidad_copias, 1)),
    'PARTIALLY_COMPLETED',
    COALESCE(MIN(d.fecha_ingreso), NOW()),
    NOW()
FROM disco d
WHERE d.numero_factura_compra IS NOT NULL
  AND BTRIM(d.numero_factura_compra) <> ''
  AND d.procedencia IN ('Future', 'VINYL_FUTURE', 'VinylFuture')
  AND NOT EXISTS (
      SELECT 1 FROM pedido p
      WHERE p.origen_importacion = 'vinylfuture'
        AND p.numero_factura = d.numero_factura_compra
  )
GROUP BY d.numero_factura_compra;

WITH candidates AS (
    SELECT
        p.id_pedido,
        d.codigo_interno,
        d.artista,
        d.album,
        LEFT(COALESCE(d.formato, d.tipo_disco), 10) AS formato,
        d.costo,
        COALESCE(d.cantidad_copias, 1) AS cantidad,
        COALESCE(d.costo, 0) * COALESCE(d.cantidad_copias, 1) AS total_linea_eur,
        d.id_disco,
        ROW_NUMBER() OVER (
            PARTITION BY p.id_pedido
            ORDER BY d.id_disco
        ) AS source_line
    FROM disco d
    JOIN pedido p
      ON p.origen_importacion = 'vinylfuture'
     AND p.numero_factura = d.numero_factura_compra
    WHERE d.numero_factura_compra IS NOT NULL
      AND BTRIM(d.numero_factura_compra) <> ''
      AND d.procedencia IN ('Future', 'VINYL_FUTURE', 'VinylFuture')
      AND NOT EXISTS (
          SELECT 1 FROM pedido_item pi
          WHERE pi.id_pedido = p.id_pedido
            AND pi.id_disco = d.id_disco
      )
)
INSERT INTO pedido_item (
    id_pedido, codigo, artista, titulo, formato,
    precio_unitario_eur, cantidad, total_linea_eur,
    id_disco, linea_factura, enrich_status
)
SELECT
    c.id_pedido,
    c.codigo_interno,
    c.artista,
    c.album,
    c.formato,
    c.costo,
    c.cantidad,
    c.total_linea_eur,
    c.id_disco,
    (COALESCE((
        SELECT MAX(existing.linea_factura)
        FROM pedido_item existing
        WHERE existing.id_pedido = c.id_pedido
    ), 0) + c.source_line)::INTEGER,
    'IMPORTED'
FROM candidates c;
