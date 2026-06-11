-- Stage 4: ventas multi-disco
-- Crear tabla detalle_venta
CREATE TABLE IF NOT EXISTS detalle_venta (
  id_detalle      BIGSERIAL PRIMARY KEY,
  id_venta        BIGINT NOT NULL REFERENCES venta(id_venta) ON DELETE CASCADE,
  id_disco        BIGINT NOT NULL REFERENCES disco(id_disco),
  precio_unitario NUMERIC(10,2) NOT NULL,
  artista_snap    VARCHAR(255),
  album_snap      VARCHAR(255),
  codigo_snap     VARCHAR(100)
);

-- Hacer nullable id_disco en venta (backward compat con ventas existentes)
ALTER TABLE venta ALTER COLUMN id_disco DROP NOT NULL;

-- Agregar subtotal y descuento a venta
ALTER TABLE venta ADD COLUMN IF NOT EXISTS subtotal NUMERIC(10,2);
ALTER TABLE venta ADD COLUMN IF NOT EXISTS descuento_porcentaje NUMERIC(5,2) DEFAULT 0;

-- Backfill detalle_venta desde ventas existentes que tenían un solo disco
INSERT INTO detalle_venta (id_venta, id_disco, precio_unitario, artista_snap, album_snap, codigo_snap)
SELECT v.id_venta, v.id_disco, COALESCE(v.precio_venta, 0), d.artista, d.album, d.codigo_interno
FROM venta v
JOIN disco d ON v.id_disco = d.id_disco
WHERE v.id_disco IS NOT NULL;

-- Backfill subtotal en ventas existentes
UPDATE venta SET subtotal = COALESCE(precio_venta, 0), descuento_porcentaje = 0
WHERE subtotal IS NULL;
