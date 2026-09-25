-- Prompt 3: immutable New/Used commercial classification for sale details.
-- Historical rows intentionally remain NULL; no speculative backfill is safe.
ALTER TABLE detalle_venta
    ADD COLUMN IF NOT EXISTS clasificacion_item VARCHAR(10);
