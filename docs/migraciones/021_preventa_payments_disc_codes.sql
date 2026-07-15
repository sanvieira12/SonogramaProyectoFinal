ALTER TABLE pre_venta
    ADD COLUMN IF NOT EXISTS codigo_disco VARCHAR(255),
    ADD COLUMN IF NOT EXISTS codigo_disco_normalizado VARCHAR(255),
    ADD COLUMN IF NOT EXISTS id_venta_pago BIGINT NULL,
    ADD COLUMN IF NOT EXISTS fecha_pago TIMESTAMP NULL;

ALTER TABLE venta
    ADD COLUMN IF NOT EXISTS origen VARCHAR(30),
    ADD COLUMN IF NOT EXISTS id_pre_venta_origen BIGINT NULL;

UPDATE pre_venta
SET codigo_disco_normalizado = LOWER(REGEXP_REPLACE(TRIM(codigo_disco), '\s+', ' ', 'g'))
WHERE codigo_disco IS NOT NULL
  AND TRIM(codigo_disco) <> ''
  AND codigo_disco_normalizado IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_pre_venta_id_venta_pago
    ON pre_venta(id_venta_pago) WHERE id_venta_pago IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_venta_id_pre_venta_origen
    ON venta(id_pre_venta_origen) WHERE id_pre_venta_origen IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pre_venta_codigo_normalizado_pendiente
    ON pre_venta(codigo_disco_normalizado)
    WHERE codigo_disco_normalizado IS NOT NULL AND id_disco IS NULL AND estado <> 'PAGADA';
CREATE INDEX IF NOT EXISTS idx_pre_venta_id_disco ON pre_venta(id_disco);

DO $$ BEGIN
    ALTER TABLE pre_venta ADD CONSTRAINT fk_pre_venta_venta_pago
        FOREIGN KEY (id_venta_pago) REFERENCES venta(id_venta);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE venta ADD CONSTRAINT fk_venta_pre_venta_origen
        FOREIGN KEY (id_pre_venta_origen) REFERENCES pre_venta(id_pre_venta);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
