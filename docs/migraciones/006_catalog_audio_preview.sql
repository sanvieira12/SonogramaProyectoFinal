-- Stage 7: Audio previews permanentes por producto del catálogo
-- + columna staging de tracks en pedido_item

ALTER TABLE pedido_item ADD COLUMN IF NOT EXISTS tracks_json TEXT;

CREATE TABLE IF NOT EXISTS catalog_audio_preview (
    id                BIGSERIAL PRIMARY KEY,
    id_disco          BIGINT NOT NULL REFERENCES disco(id_disco) ON DELETE CASCADE,
    track_name        VARCHAR(500),
    track_position    VARCHAR(10),
    audio_url         VARCHAR(1000) NOT NULL,
    duration_seconds  INTEGER,
    source            VARCHAR(50) NOT NULL DEFAULT 'vinylfuture',
    status            VARCHAR(20) NOT NULL DEFAULT 'FOUND',
    error_message     VARCHAR(500),
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cap_disco ON catalog_audio_preview(id_disco);
