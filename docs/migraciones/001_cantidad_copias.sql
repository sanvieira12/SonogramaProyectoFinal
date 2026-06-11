-- Migración 001: agregar campo cantidad_copias a la tabla disco
-- Ejecutar SOLO en producción, una vez, antes del próximo deploy.
-- En desarrollo local NO hace falta: ddl-auto=update lo crea solo.

ALTER TABLE disco
  ADD COLUMN IF NOT EXISTS cantidad_copias INTEGER DEFAULT 1;

-- Inicializa todos los discos existentes con 1 copia.
-- Si algún disco ya estaba en SIN_STOCK o VENDIDO, ponerlo en 0:
UPDATE disco SET cantidad_copias = 0
  WHERE estado IN ('SIN_STOCK', 'VENDIDO');
