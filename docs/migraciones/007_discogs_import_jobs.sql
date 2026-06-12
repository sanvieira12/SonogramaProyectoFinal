-- Importación progresiva desde Excel con trazabilidad de links Discogs.
CREATE TABLE IF NOT EXISTS discogs_import_job (
    id_discogs_import_job BIGSERIAL PRIMARY KEY,
    nombre_archivo        VARCHAR(500) NOT NULL,
    nombre_hoja           VARCHAR(255),
    status                VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    error_message         TEXT,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS discogs_import_row (
    id_discogs_import_row     BIGSERIAL PRIMARY KEY,
    id_discogs_import_job     BIGINT NOT NULL REFERENCES discogs_import_job(id_discogs_import_job) ON DELETE CASCADE,
    source_excel_row_number   INTEGER NOT NULL,
    visible_cell_value        TEXT,
    hyperlink_url             TEXT,
    normalized_discogs_url    TEXT,
    url_source                VARCHAR(20),
    discogs_type              VARCHAR(20),
    discogs_id                BIGINT,
    master_id                 BIGINT,
    resolved_release_id       BIGINT,
    artist                    VARCHAR(255),
    title                     VARCHAR(500),
    release_year              INTEGER,
    genre                     VARCHAR(255),
    label                     VARCHAR(255),
    country                   VARCHAR(255),
    style                     VARCHAR(255),
    format                    VARCHAR(255),
    image_url                 TEXT,
    preview_url               TEXT,
    tracklist                 TEXT,
    status                    VARCHAR(50) NOT NULL,
    error_message             TEXT,
    retry_count               INTEGER NOT NULL DEFAULT 0,
    imported_catalog_product_id BIGINT REFERENCES disco(id_disco)
);

CREATE INDEX IF NOT EXISTS idx_discogs_import_row_job
    ON discogs_import_row(id_discogs_import_job);
CREATE INDEX IF NOT EXISTS idx_discogs_import_row_status
    ON discogs_import_row(status);
CREATE INDEX IF NOT EXISTS idx_discogs_import_row_source
    ON discogs_import_row(discogs_type, discogs_id);
