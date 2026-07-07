ALTER TABLE detalle_venta
    ADD COLUMN IF NOT EXISTS copy_ids_snapshot TEXT;

ALTER TABLE disco_qr_copy
    ADD COLUMN IF NOT EXISTS estado VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE';

UPDATE disco_qr_copy
SET estado = 'DISPONIBLE'
WHERE estado IS NULL OR TRIM(estado) = '';

CREATE TABLE IF NOT EXISTS pre_venta (
    id_pre_venta BIGSERIAL PRIMARY KEY,
    id_cliente BIGINT NOT NULL REFERENCES cliente(id_cliente),
    id_disco BIGINT NULL REFERENCES disco(id_disco),
    fecha DATE NOT NULL,
    cantidad INTEGER NOT NULL DEFAULT 1,
    precio NUMERIC(12,2) NOT NULL,
    estado VARCHAR(40) NOT NULL DEFAULT 'PENDIENTE',
    notas TEXT,
    artista_snap VARCHAR(255),
    album_snap VARCHAR(255),
    descripcion_snap TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pre_venta_fecha ON pre_venta(fecha DESC);
CREATE INDEX IF NOT EXISTS idx_pre_venta_cliente ON pre_venta(id_cliente);

CREATE TABLE IF NOT EXISTS gasto_tienda (
    id_gasto BIGSERIAL PRIMARY KEY,
    fecha DATE NOT NULL,
    descripcion VARCHAR(255) NOT NULL,
    monto NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_gasto_tienda_fecha ON gasto_tienda(fecha DESC);
