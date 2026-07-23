ALTER TABLE gasto_tienda
    ADD COLUMN IF NOT EXISTS categoria VARCHAR(30);

CREATE INDEX IF NOT EXISTS idx_gasto_tienda_categoria
    ON gasto_tienda(categoria);
