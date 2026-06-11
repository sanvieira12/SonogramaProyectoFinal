-- Stage 5: ShippingOrder cost breakdown fields
ALTER TABLE shipping_order ADD COLUMN IF NOT EXISTS subtotal NUMERIC(10,2);
ALTER TABLE shipping_order ADD COLUMN IF NOT EXISTS impuestos NUMERIC(10,2);
ALTER TABLE shipping_order ADD COLUMN IF NOT EXISTS otros_costos NUMERIC(10,2);
ALTER TABLE shipping_order ADD COLUMN IF NOT EXISTS total_estimado NUMERIC(10,2);
