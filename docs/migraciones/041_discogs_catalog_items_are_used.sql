-- Discogs describe la graduación física por separado; comercialmente,
-- todos los discos ingresados por este importador pertenecen a USADO.
UPDATE disco d
SET condicion = 'USADO'
WHERE d.condicion IS DISTINCT FROM 'USADO'
  AND EXISTS (
      SELECT 1
      FROM discogs_import_row r
      WHERE r.imported_catalog_product_id = d.id_disco
  );
