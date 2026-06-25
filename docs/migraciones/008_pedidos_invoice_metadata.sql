ALTER TABLE pedido ADD COLUMN IF NOT EXISTS envio VARCHAR(255);
ALTER TABLE pedido ADD COLUMN IF NOT EXISTS unidad_peso VARCHAR(20);
ALTER TABLE pedido ADD COLUMN IF NOT EXISTS iva NUMERIC(10,2);

UPDATE pedido SET tipo_cambio = 50 WHERE tipo_cambio IS NULL;
UPDATE pedido SET extra_costo_simple = 5 WHERE extra_costo_simple IS NULL;
UPDATE pedido SET extra_costo_doble = 8 WHERE extra_costo_doble IS NULL;
UPDATE pedido SET markup_simple = 1.6 WHERE markup_simple IS NULL;
UPDATE pedido SET markup_doble = 1.4 WHERE markup_doble IS NULL;
