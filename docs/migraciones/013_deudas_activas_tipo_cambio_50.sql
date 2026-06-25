ALTER TABLE deuda
    ADD COLUMN IF NOT EXISTS activa BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE deuda
SET activa = TRUE
WHERE activa IS NULL;

CREATE INDEX IF NOT EXISTS idx_deuda_activa_estado_fecha
    ON deuda (activa, estado_pago, fecha_deuda);

UPDATE pedido
SET tipo_cambio = 50
WHERE tipo_cambio IS NULL OR tipo_cambio = 49;
