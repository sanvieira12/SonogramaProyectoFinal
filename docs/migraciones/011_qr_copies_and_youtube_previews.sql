ALTER TABLE catalog_audio_preview
    ALTER COLUMN audio_url DROP NOT NULL;

ALTER TABLE catalog_audio_preview
    ADD COLUMN IF NOT EXISTS youtube_url VARCHAR(1000);

ALTER TABLE discogs_import_row
    ADD COLUMN IF NOT EXISTS tracks_json TEXT;

CREATE TABLE IF NOT EXISTS disco_qr_copy (
    id          BIGSERIAL PRIMARY KEY,
    id_disco    BIGINT NOT NULL REFERENCES disco(id_disco) ON DELETE CASCADE,
    copy_number INTEGER NOT NULL,
    codigo_qr   VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_disco_qr_copy_code UNIQUE (codigo_qr),
    CONSTRAINT uk_disco_qr_copy_number UNIQUE (id_disco, copy_number)
);

INSERT INTO disco_qr_copy (id_disco, copy_number, codigo_qr)
SELECT id_disco, 1, codigo_qr
FROM disco
WHERE codigo_qr IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO disco_qr_copy (id_disco, copy_number, codigo_qr)
SELECT d.id_disco, n.copy_number,
       md5(random()::text || clock_timestamp()::text || d.id_disco::text || n.copy_number::text)
FROM disco d
CROSS JOIN LATERAL generate_series(
    2,
    GREATEST(COALESCE(d.cantidad_copias, 1), 1)
) AS n(copy_number)
ON CONFLICT DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_disco_qr_copy_disco ON disco_qr_copy(id_disco);
