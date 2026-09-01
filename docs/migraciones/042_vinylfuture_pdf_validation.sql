-- Vinyl Future Phase 1: retain the source row and its invoice-reading status.
ALTER TABLE pedido_item
    ADD COLUMN IF NOT EXISTS pagina_fuente INTEGER,
    ADD COLUMN IF NOT EXISTS texto_fuente TEXT,
    ADD COLUMN IF NOT EXISTS estado_lectura VARCHAR(40),
    ADD COLUMN IF NOT EXISTS motivo_revision TEXT,
    ADD COLUMN IF NOT EXISTS cantidad_estimada INTEGER;

UPDATE pedido_item
SET estado_lectura = 'PARSED'
WHERE estado_lectura IS NULL;

CREATE INDEX IF NOT EXISTS idx_pedido_item_estado_lectura
    ON pedido_item(id_pedido, estado_lectura);
