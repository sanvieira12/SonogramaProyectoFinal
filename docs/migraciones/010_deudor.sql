CREATE TABLE IF NOT EXISTS deudor (
    id                    BIGSERIAL PRIMARY KEY,
    nombre_deudor         VARCHAR(255) NOT NULL,
    id_cliente            BIGINT,
    monto_original        VARCHAR(255),
    monto_uyu             NUMERIC(15, 2),
    fecha_estimada        DATE,
    notas                 TEXT,
    descripcion_discos    TEXT,
    estado                VARCHAR(50) DEFAULT 'PENDIENTE',
    fuente                VARCHAR(50) DEFAULT 'IMPORTACION_EXCEL',
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW()
);
