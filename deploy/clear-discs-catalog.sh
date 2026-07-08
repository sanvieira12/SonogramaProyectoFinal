#!/usr/bin/env bash
set -euo pipefail

MODE="${1:---dry-run}"
SCOPE="${2:---all-catalog}"
PG_CONTAINER="${PG_CONTAINER:-sonograma-postgres}"
DB_NAME="${SPRING_DATASOURCE_DATABASE:-sonograma_db}"
DB_USER="${SPRING_DATASOURCE_USERNAME:-sonograma_user}"
MEDIA_DIR="${VINYLFUTURE_MEDIA_DIR:-/opt/sonograma/data/vinylfuture-media}"
DISCOGS_COVERS_DIR="${DISCOGS_COVERS_DIR:-/opt/sonograma/data/discogs-covers}"

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
echo "Seguridad: preserva clientes, deudas, ventas e historial; limpia solo catálogo/importaciones."

docker exec -i "$PG_CONTAINER" psql \
  -v ON_ERROR_STOP=1 \
  -v vinylfuture_only="$([[ "$SCOPE" == "--vinylfuture-only" ]] && echo true || echo false)" \
  -U "$DB_USER" \
  -d "$DB_NAME" <<SQL
BEGIN;

CREATE TEMP TABLE target_discos ON COMMIT DROP AS
SELECT d.id_disco
FROM disco d
WHERE (NOT :vinylfuture_only OR UPPER(COALESCE(d.procedencia, '')) = 'VINYL_FUTURE');

SELECT 'discos_catalogo_a_borrar' AS item, count(*) AS cantidad FROM target_discos;

DO \$\$
DECLARE
  affected integer;
BEGIN
  UPDATE detalle_venta
  SET id_disco = NULL
  WHERE id_disco IN (SELECT id_disco FROM target_discos);
  GET DIAGNOSTICS affected = ROW_COUNT;
  RAISE NOTICE 'detalle_venta desvinculados: %', affected;

  UPDATE venta
  SET id_disco = NULL
  WHERE id_disco IN (SELECT id_disco FROM target_discos);
  GET DIAGNOSTICS affected = ROW_COUNT;
  RAISE NOTICE 'venta desvinculados: %', affected;

  UPDATE pre_venta
  SET id_disco = NULL
  WHERE id_disco IN (SELECT id_disco FROM target_discos);
  GET DIAGNOSTICS affected = ROW_COUNT;
  RAISE NOTICE 'pre_venta desvinculados: %', affected;

  DELETE FROM reserva
  WHERE id_disco IN (SELECT id_disco FROM target_discos);
  GET DIAGNOSTICS affected = ROW_COUNT;
  RAISE NOTICE 'reserva borrados: %', affected;

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

  IF NOT :vinylfuture_only THEN
    DELETE FROM discogs_import_row;
    GET DIAGNOSTICS affected = ROW_COUNT;
    RAISE NOTICE 'discogs_import_row historial borrado: %', affected;

    DELETE FROM discogs_import_job;
    GET DIAGNOSTICS affected = ROW_COUNT;
    RAISE NOTICE 'discogs_import_job borrados: %', affected;
  END IF;

  DELETE FROM disco
  WHERE id_disco IN (SELECT id_disco FROM target_discos);
  GET DIAGNOSTICS affected = ROW_COUNT;
  RAISE NOTICE 'disco borrados: %', affected;
END
\$\$;

$TX_END
SQL

if [[ "$MODE" == "--dry-run" ]]; then
  echo "Dry-run completo: no se confirmó ningún borrado."
else
  if [[ -d "$MEDIA_DIR" ]]; then
    find "$MEDIA_DIR" -mindepth 1 -delete
    echo "Assets VinylFuture eliminados de $MEDIA_DIR."
  fi
  if [[ "$SCOPE" == "--all-catalog" && -d "$DISCOGS_COVERS_DIR" ]]; then
    find "$DISCOGS_COVERS_DIR" -mindepth 1 -delete
    echo "Portadas Discogs eliminadas de $DISCOGS_COVERS_DIR."
  fi
  echo "Limpieza de catálogo confirmada para alcance $SCOPE."
fi
