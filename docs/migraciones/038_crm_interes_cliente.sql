-- Manual customer interests are the only CRM data that cannot be derived from sales.
CREATE TABLE IF NOT EXISTS crm_interes_cliente (
    id_interes BIGSERIAL PRIMARY KEY,
    id_cliente BIGINT NOT NULL REFERENCES cliente(id_cliente) ON DELETE CASCADE,
    tipo VARCHAR(30) NOT NULL DEFAULT 'LIBRE',
    texto VARCHAR(500) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_crm_interes_cliente_activo
    ON crm_interes_cliente (id_cliente, activo, fecha_creacion DESC);

CREATE INDEX IF NOT EXISTS idx_venta_crm_cliente_estado_fecha
    ON venta (id_cliente, estado, fecha_venta DESC);

CREATE INDEX IF NOT EXISTS idx_detalle_venta_id_venta
    ON detalle_venta (id_venta);

CREATE INDEX IF NOT EXISTS idx_disco_qr_copy_disponibilidad
    ON disco_qr_copy (id_disco, estado);
