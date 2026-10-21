BEGIN;

-- Keep the legacy table stable while parent rows are created and linked. DML
-- that arrives during the migration waits and then sees the installed triggers.
SET LOCAL lock_timeout = '30s';
LOCK TABLE pre_venta IN SHARE ROW EXCLUSIVE MODE;

CREATE TABLE IF NOT EXISTS pre_venta_transaccion (
    id_pre_venta_transaccion BIGSERIAL PRIMARY KEY,
    id_cliente BIGINT NOT NULL REFERENCES cliente(id_cliente),
    fecha DATE NOT NULL,
    notas TEXT,
    idempotency_key VARCHAR(120) NOT NULL,
    request_fingerprint VARCHAR(64),
    legacy_id_pre_venta BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_pre_venta_transaccion_idempotency
    ON pre_venta_transaccion(idempotency_key);
CREATE UNIQUE INDEX IF NOT EXISTS uq_pre_venta_transaccion_legacy
    ON pre_venta_transaccion(legacy_id_pre_venta)
    WHERE legacy_id_pre_venta IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pre_venta_transaccion_cliente
    ON pre_venta_transaccion(id_cliente);
CREATE INDEX IF NOT EXISTS idx_pre_venta_transaccion_fecha
    ON pre_venta_transaccion(fecha DESC);

ALTER TABLE pre_venta
    ADD COLUMN IF NOT EXISTS id_pre_venta_transaccion BIGINT,
    ADD COLUMN IF NOT EXISTS operation_key VARCHAR(120);

CREATE UNIQUE INDEX IF NOT EXISTS uq_pre_venta_transaction_item_operation
    ON pre_venta(id_pre_venta_transaccion, operation_key)
    WHERE operation_key IS NOT NULL;

-- Keeps the legacy single-item writer compatible during a rolling application
-- replacement. The new application always supplies the parent explicitly.
CREATE OR REPLACE FUNCTION ensure_pre_venta_transaccion()
RETURNS TRIGGER AS $$
DECLARE
    generated_transaction_id BIGINT;
BEGIN
    IF NEW.id_pre_venta_transaccion IS NULL THEN
        INSERT INTO pre_venta_transaccion (
            id_cliente, fecha, notas, idempotency_key, legacy_id_pre_venta,
            created_at, updated_at
        ) VALUES (
            NEW.id_cliente,
            NEW.fecha,
            NEW.notas,
            'legacy-db-' || NEW.id_pre_venta,
            NEW.id_pre_venta,
            COALESCE(NEW.created_at, NOW()),
            COALESCE(NEW.updated_at, NOW())
        )
        RETURNING id_pre_venta_transaccion INTO generated_transaction_id;
        NEW.id_pre_venta_transaccion := generated_transaction_id;
    ELSIF NOT EXISTS (
        SELECT 1
        FROM pre_venta_transaccion t
        WHERE t.id_pre_venta_transaccion = NEW.id_pre_venta_transaccion
          AND t.id_cliente = NEW.id_cliente
    ) THEN
        RAISE EXCEPTION 'El cliente del ítem no coincide con la transacción de pre-venta';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ensure_pre_venta_transaccion ON pre_venta;
CREATE TRIGGER trg_ensure_pre_venta_transaccion
BEFORE INSERT OR UPDATE ON pre_venta
FOR EACH ROW
EXECUTE FUNCTION ensure_pre_venta_transaccion();

-- A legacy DELETE knows only the item. Remove the generated parent after its
-- last item disappears so rolling deployments cannot expose empty transactions.
CREATE OR REPLACE FUNCTION delete_empty_pre_venta_transaccion()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.id_pre_venta_transaccion IS NOT NULL THEN
        DELETE FROM pre_venta_transaccion t
        WHERE t.id_pre_venta_transaccion = OLD.id_pre_venta_transaccion
          AND NOT EXISTS (
              SELECT 1
              FROM pre_venta p
              WHERE p.id_pre_venta_transaccion = OLD.id_pre_venta_transaccion
          );
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_delete_empty_pre_venta_transaccion ON pre_venta;
CREATE TRIGGER trg_delete_empty_pre_venta_transaccion
AFTER DELETE ON pre_venta
FOR EACH ROW
EXECUTE FUNCTION delete_empty_pre_venta_transaccion();

INSERT INTO pre_venta_transaccion (
    id_cliente, fecha, notas, idempotency_key, legacy_id_pre_venta,
    created_at, updated_at
)
SELECT
    p.id_cliente,
    p.fecha,
    p.notas,
    'legacy-pre-venta-' || p.id_pre_venta,
    p.id_pre_venta,
    p.created_at,
    p.updated_at
FROM pre_venta p
WHERE p.id_pre_venta_transaccion IS NULL
ON CONFLICT (legacy_id_pre_venta) WHERE legacy_id_pre_venta IS NOT NULL DO NOTHING;

UPDATE pre_venta p
SET id_pre_venta_transaccion = t.id_pre_venta_transaccion
FROM pre_venta_transaccion t
WHERE p.id_pre_venta_transaccion IS NULL
  AND t.legacy_id_pre_venta = p.id_pre_venta;

DO $$ BEGIN
    ALTER TABLE pre_venta ADD CONSTRAINT fk_pre_venta_transaccion
        FOREIGN KEY (id_pre_venta_transaccion)
        REFERENCES pre_venta_transaccion(id_pre_venta_transaccion);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

ALTER TABLE pre_venta
    ALTER COLUMN id_pre_venta_transaccion SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pre_venta_transaccion_item
    ON pre_venta(id_pre_venta_transaccion);

COMMIT;
