-- Orders-only invoice fidelity: preserve source line order and full description.
ALTER TABLE pedido_item ADD COLUMN IF NOT EXISTS linea_factura INTEGER;
ALTER TABLE pedido_item ADD COLUMN IF NOT EXISTS descripcion_original VARCHAR(1000);

WITH numbered AS (
    SELECT id_pedido_item,
           ROW_NUMBER() OVER (PARTITION BY id_pedido ORDER BY id_pedido_item) AS line_number
    FROM pedido_item
)
UPDATE pedido_item pi
SET linea_factura = numbered.line_number
FROM numbered
WHERE pi.id_pedido_item = numbered.id_pedido_item
  AND pi.linea_factura IS NULL;

UPDATE pedido_item
SET descripcion_original = CASE
    WHEN formato IS NULL OR BTRIM(formato) = '' THEN CONCAT_WS(' - ', artista, titulo)
    ELSE CONCAT_WS(' - ', artista, titulo) || ' ' || formato
END
WHERE descripcion_original IS NULL;

UPDATE pedido_item
SET total_linea_eur = precio_unitario_eur * cantidad
WHERE precio_unitario_eur IS NOT NULL
  AND cantidad IS NOT NULL;

ALTER TABLE pedido_item ALTER COLUMN linea_factura SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pedido_item_invoice_order
    ON pedido_item(id_pedido, linea_factura, id_pedido_item);
