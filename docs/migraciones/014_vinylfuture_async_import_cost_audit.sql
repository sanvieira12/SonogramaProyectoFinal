ALTER TABLE disco
    ADD COLUMN IF NOT EXISTS costo_moneda VARCHAR(10),
    ADD COLUMN IF NOT EXISTS numero_factura_compra VARCHAR(255),
    ADD COLUMN IF NOT EXISTS fecha_factura_compra DATE;

UPDATE disco
SET costo_moneda = 'UYU'
WHERE costo IS NOT NULL
  AND costo_moneda IS NULL
  AND (procedencia IS NULL OR procedencia <> 'VINYL_FUTURE');
