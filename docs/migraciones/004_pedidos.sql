-- Stage 6: Pedidos de importación (facturas deejay.de/VinylFuture)
CREATE TABLE IF NOT EXISTS pedido (
    id_pedido          BIGSERIAL PRIMARY KEY,
    numero_factura     VARCHAR(100),
    fecha_factura      DATE,
    proveedor          VARCHAR(255),
    pago               VARCHAR(100),
    moneda             VARCHAR(10) DEFAULT 'EUR',
    peso_total_kg      NUMERIC(8,3),
    terminos_venta     VARCHAR(500),
    codigo_arancel     VARCHAR(100),
    eori_no            VARCHAR(100),
    nombre_archivo     VARCHAR(500),
    texto_extraido     TEXT,
    franqueo           NUMERIC(10,2),
    tarifas            NUMERIC(10,2),
    neto               NUMERIC(10,2),
    total              NUMERIC(10,2),
    cantidad_total_pdf INTEGER,
    import_status      VARCHAR(50) NOT NULL DEFAULT 'PARSED',
    tipo_cambio        NUMERIC(10,4),
    extra_costo_simple NUMERIC(10,2),
    extra_costo_doble  NUMERIC(10,2),
    markup_simple      NUMERIC(8,4),
    markup_doble       NUMERIC(8,4),
    created_at         TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS pedido_item (
    id_pedido_item    BIGSERIAL PRIMARY KEY,
    id_pedido         BIGINT NOT NULL REFERENCES pedido(id_pedido) ON DELETE CASCADE,
    codigo            VARCHAR(100),
    artista           VARCHAR(255),
    titulo            VARCHAR(500),
    formato           VARCHAR(10),
    precio_unitario_eur NUMERIC(10,2),
    cantidad          INTEGER,
    total_linea_eur   NUMERIC(10,2),
    extra_costo_eur   NUMERIC(10,2),
    costo_real_eur    NUMERIC(10,2),
    costo_real_uyu    NUMERIC(10,2),
    markup            NUMERIC(8,4),
    precio_final_uyu  NUMERIC(10,2),
    portada_url       VARCHAR(1000),
    id_disco          BIGINT REFERENCES disco(id_disco),
    enrich_status     VARCHAR(50) DEFAULT 'PENDING'
);

CREATE INDEX IF NOT EXISTS idx_pedido_import_status ON pedido(import_status);
CREATE INDEX IF NOT EXISTS idx_pedido_item_pedido   ON pedido_item(id_pedido);
