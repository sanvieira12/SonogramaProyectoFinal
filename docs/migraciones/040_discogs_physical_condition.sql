ALTER TABLE disco
    ADD COLUMN IF NOT EXISTS condicion_fisica VARCHAR(50);

-- Recupera la graduación física de importaciones Discogs ya realizadas.
-- La categoría comercial permanece en disco.condicion (USADO/NUEVO/etc.).
UPDATE disco d
SET condicion_fisica = LEFT(UPPER(BTRIM(source.manual_condition)), 50)
FROM (
    SELECT DISTINCT ON (imported_catalog_product_id)
           imported_catalog_product_id,
           manual_condition
    FROM discogs_import_row
    WHERE imported_catalog_product_id IS NOT NULL
      AND manual_condition IS NOT NULL
      AND BTRIM(manual_condition) <> ''
    ORDER BY imported_catalog_product_id, id_discogs_import_row DESC
) source
WHERE d.id_disco = source.imported_catalog_product_id
  AND (d.condicion_fisica IS NULL OR BTRIM(d.condicion_fisica) = '');
