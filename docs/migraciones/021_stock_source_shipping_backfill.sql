UPDATE disco
SET procedencia = 'Future'
WHERE procedencia IS NULL
   OR BTRIM(procedencia) = ''
   OR UPPER(REPLACE(BTRIM(procedencia), ' ', '_')) IN ('VINYL_FUTURE', 'FUTURE');

UPDATE pedido p
SET proveedor = 'Future',
    envio = 'UPS'
WHERE EXISTS (
    SELECT 1
    FROM disco d
    WHERE d.numero_factura_compra IS NOT NULL
      AND d.numero_factura_compra = p.numero_factura
      AND d.procedencia = 'Future'
)
  AND (
      p.proveedor IS NULL
      OR BTRIM(p.proveedor) = ''
      OR UPPER(REPLACE(REPLACE(BTRIM(p.proveedor), '.', ''), ' ', '_')) IN ('VINYL_FUTURE', 'VINYLFUTURE', 'FUTURE', 'DEEJAYDE')
      OR p.envio IS DISTINCT FROM 'UPS'
  );
