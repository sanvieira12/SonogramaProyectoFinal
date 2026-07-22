-- Keep receipt numbers at payment level and make client retries idempotent.
-- Both changes are nullable so historical payments remain valid.

ALTER TABLE pago_deuda
    ADD COLUMN IF NOT EXISTS numero_recibo VARCHAR(255);

ALTER TABLE pago_deuda
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uq_pago_deuda_idempotency
    ON pago_deuda (id_deuda, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
