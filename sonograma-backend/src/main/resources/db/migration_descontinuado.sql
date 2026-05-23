-- Migrar discos con estado DESCONTINUADO a SIN_STOCK
UPDATE disco SET estado = 'SIN_STOCK' WHERE estado = 'DESCONTINUADO';

-- Migrar discos con estado FUERA_STOCK a SIN_STOCK
UPDATE disco SET estado = 'SIN_STOCK' WHERE estado = 'FUERA_STOCK';
