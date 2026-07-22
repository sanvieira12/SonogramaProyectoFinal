-- Preserve debt payments as financial audit records when they are reversed.
-- Reversed rows are excluded from balances, income, reports and exports.

ALTER TABLE pago_deuda
    ADD COLUMN IF NOT EXISTS anulado BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE pago_deuda
    ADD COLUMN IF NOT EXISTS fecha_anulacion TIMESTAMP;

ALTER TABLE pago_deuda
    ADD COLUMN IF NOT EXISTS anulado_por VARCHAR(150);

UPDATE pago_deuda
SET anulado = FALSE
WHERE anulado IS NULL;

CREATE INDEX IF NOT EXISTS idx_pago_deuda_vigentes
    ON pago_deuda (id_deuda, anulado, fecha_pago, created_at);
