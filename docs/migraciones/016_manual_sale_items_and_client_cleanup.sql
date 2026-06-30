ALTER TABLE detalle_venta
    ALTER COLUMN id_disco DROP NOT NULL;

ALTER TABLE detalle_venta
    ADD COLUMN IF NOT EXISTS cantidad INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS descripcion_snap VARCHAR(500),
    ADD COLUMN IF NOT EXISTS manual_item BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE detalle_venta
SET cantidad = 1
WHERE cantidad IS NULL OR cantidad < 1;

UPDATE detalle_venta
SET manual_item = TRUE
WHERE id_disco IS NULL;

UPDATE cliente
SET activo = FALSE
WHERE activo = TRUE
  AND LOWER(TRIM(nombre)) IN ('santi', 'noelia')
  AND (apellido IS NULL OR TRIM(apellido) = '');
