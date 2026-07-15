-- Reconstruct historical VinylFuture order headers/items from catalog rows.
-- The original PDFs are not available for these imports, so these orders are
-- explicitly marked partial and use the persisted catalog quantities.
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

INSERT INTO pedido_item (
    id_pedido, codigo, artista, titulo, formato,
    precio_unitario_eur, cantidad, total_linea_eur,
    id_disco, enrich_status
)
SELECT
    p.id_pedido,
    d.codigo_interno,
    d.artista,
    d.album,
    COALESCE(d.formato, d.tipo_disco),
    d.costo,
    COALESCE(d.cantidad_copias, 1),
    COALESCE(d.costo, 0) * COALESCE(d.cantidad_copias, 1),
    d.id_disco,
    'IMPORTED'
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
  );
