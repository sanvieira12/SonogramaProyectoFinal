-- Cambios no destructivos para Clientes, Deudas, Pedidos y Notas.

ALTER TABLE cliente
    ADD COLUMN IF NOT EXISTS departamento VARCHAR(100),
    ADD COLUMN IF NOT EXISTS sucursal_dac VARCHAR(180);

ALTER TABLE deuda
    ALTER COLUMN id_cliente DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS numero_factura VARCHAR(255),
    ADD COLUMN IF NOT EXISTS nombre_deudor_manual VARCHAR(255),
    ADD COLUMN IF NOT EXISTS mail_manual VARCHAR(255),
    ADD COLUMN IF NOT EXISTS instagram_manual VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ci_manual VARCHAR(255),
    ADD COLUMN IF NOT EXISTS descripcion TEXT,
    ADD COLUMN IF NOT EXISTS fecha_deuda DATE,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

UPDATE deuda
SET fecha_deuda = COALESCE(fecha_deuda, fecha_venta, fecha_creacion::date),
    updated_at = COALESCE(updated_at, fecha_creacion, NOW())
WHERE fecha_deuda IS NULL OR updated_at IS NULL;

CREATE TABLE IF NOT EXISTS pago_deuda (
    id_pago_deuda BIGSERIAL PRIMARY KEY,
    id_deuda BIGINT NOT NULL REFERENCES deuda(id_deuda),
    monto NUMERIC(10,2) NOT NULL,
    fecha_pago DATE NOT NULL,
    notas VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE pedido
    ADD COLUMN IF NOT EXISTS pdf_original_filename VARCHAR(255),
    ADD COLUMN IF NOT EXISTS pdf_content_type VARCHAR(255),
    ADD COLUMN IF NOT EXISTS pdf_storage_path VARCHAR(255),
    ADD COLUMN IF NOT EXISTS pdf_uploaded_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS nota (
    id_nota BIGSERIAL PRIMARY KEY,
    titulo VARCHAR(255) NOT NULL,
    contenido TEXT,
    tags VARCHAR(255),
    fecha_nota DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    tipo_relacion VARCHAR(40) NOT NULL DEFAULT 'GENERAL',
    related_id BIGINT,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    archivada BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_deuda_cliente_estado_fecha
    ON deuda (id_cliente, estado_pago, fecha_deuda);

CREATE INDEX IF NOT EXISTS idx_deuda_manual_search
    ON deuda (nombre_deudor_manual, numero_factura);

CREATE INDEX IF NOT EXISTS idx_pago_deuda_deuda_fecha
    ON pago_deuda (id_deuda, fecha_pago DESC);

CREATE INDEX IF NOT EXISTS idx_pedido_pdf_uploaded
    ON pedido (pdf_uploaded_at);

CREATE INDEX IF NOT EXISTS idx_nota_archivada_fecha
    ON nota (archivada, pinned, fecha_nota DESC);

CREATE INDEX IF NOT EXISTS idx_nota_search
    ON nota (titulo, tags);
