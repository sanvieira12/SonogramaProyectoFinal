#!/usr/bin/env bash
set -euo pipefail

MODE="${1:---dry-run}"
SCOPE="${2:---all-catalog}"
PG_CONTAINER="${PG_CONTAINER:-sonograma-postgres}"
DB_NAME="${SPRING_DATASOURCE_DATABASE:-sonograma_db}"
DB_USER="${SPRING_DATASOURCE_USERNAME:-sonograma_user}"

if [[ "$MODE" != "--dry-run" && "$MODE" != "--execute" ]]; then
  echo "Uso: $0 [--dry-run|--execute] [--all-catalog|--vinylfuture-only]" >&2
  exit 2
fi

if [[ "$SCOPE" != "--all-catalog" && "$SCOPE" != "--vinylfuture-only" ]]; then
  echo "Uso: $0 [--dry-run|--execute] [--all-catalog|--vinylfuture-only]" >&2
  exit 2
fi

if ! docker ps --format '{{.Names}}' | grep -qx "$PG_CONTAINER"; then
  echo "No existe un contenedor PostgreSQL activo llamado '$PG_CONTAINER'." >&2
  exit 1
fi

TX_END="ROLLBACK;"
if [[ "$MODE" == "--execute" ]]; then
  TX_END="COMMIT;"
fi

echo "Modo: $MODE"
echo "Alcance: $SCOPE"
echo "Base: $DB_NAME | Usuario: $DB_USER | Contenedor: $PG_CONTAINER"
echo "Seguridad: solo discos sin ventas, detalles de venta ni reservas."

docker exec -i "$PG_CONTAINER" psql \
  -v ON_ERROR_STOP=1 \
  -v vinylfuture_only="$([[ "$SCOPE" == "--vinylfuture-only" ]] && echo true || echo false)" \
  -U "$DB_USER" \
  -d "$DB_NAME" <<SQL
BEGIN;

CREATE TEMP TABLE target_discos ON COMMIT DROP AS
SELECT d.id_disco
FROM disco d
WHERE (NOT :vinylfuture_only OR d.procedencia = 'VINYL_FUTURE')
  AND NOT EXISTS (SELECT 1 FROM venta v WHERE v.id_disco = d.id_disco)
  AND NOT EXISTS (SELECT 1 FROM detalle_venta dv WHERE dv.id_disco = d.id_disco)
  AND NOT EXISTS (SELECT 1 FROM reserva r WHERE r.id_disco = d.id_disco);

CREATE TEMP TABLE skipped_discos ON COMMIT DROP AS
SELECT d.id_disco, d.codigo_interno, d.artista, d.album
FROM disco d
WHERE (NOT :vinylfuture_only OR d.procedencia = 'VINYL_FUTURE')
  AND d.id_disco NOT IN (SELECT id_disco FROM target_discos);

SELECT 'discos_catalogo_a_borrar' AS item, count(*) AS cantidad FROM target_discos;
SELECT 'discos_catalogo_preservados_por_referencias' AS item, count(*) AS cantidad FROM skipped_discos;

DO \$\$
DECLARE
  affected integer;
BEGIN
  DELETE FROM catalog_audio_preview
  WHERE id_disco IN (SELECT id_disco FROM target_discos);
  GET DIAGNOSTICS affected = ROW_COUNT;
  RAISE NOTICE 'catalog_audio_preview borrados: %', affected;

  DELETE FROM disco_qr_copy
  WHERE id_disco IN (SELECT id_disco FROM target_discos);
  GET DIAGNOSTICS affected = ROW_COUNT;
  RAISE NOTICE 'disco_qr_copy borrados: %', affected;

  DELETE FROM movimiento_stock
  WHERE id_disco IN (SELECT id_disco FROM target_discos);
  GET DIAGNOSTICS affected = ROW_COUNT;
  RAISE NOTICE 'movimiento_stock borrados: %', affected;

  UPDATE pedido_item
  SET id_disco = NULL
  WHERE id_disco IN (SELECT id_disco FROM target_discos);
  GET DIAGNOSTICS affected = ROW_COUNT;
  RAISE NOTICE 'pedido_item desvinculados: %', affected;

  UPDATE shipping_order_item
  SET id_disco = NULL
  WHERE id_disco IN (SELECT id_disco FROM target_discos);
  GET DIAGNOSTICS affected = ROW_COUNT;
  RAISE NOTICE 'shipping_order_item desvinculados: %', affected;

  UPDATE discogs_import_row
  SET imported_catalog_product_id = NULL
  WHERE imported_catalog_product_id IN (SELECT id_disco FROM target_discos);
  GET DIAGNOSTICS affected = ROW_COUNT;
  RAISE NOTICE 'discogs_import_row desvinculados: %', affected;

  DELETE FROM disco
  WHERE id_disco IN (SELECT id_disco FROM target_discos);
  GET DIAGNOSTICS affected = ROW_COUNT;
  RAISE NOTICE 'disco borrados: %', affected;
END
\$\$;

SELECT id_disco, codigo_interno, artista, album
FROM skipped_discos
ORDER BY id_disco
LIMIT 25;

$TX_END
SQL

if [[ "$MODE" == "--dry-run" ]]; then
  echo "Dry-run completo: no se confirmó ningún borrado."
else
  echo "Limpieza de catálogo VinylFuture confirmada."
fi
