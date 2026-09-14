#!/bin/bash
###############################################################
#  Sonograma – baseline-migrations.sh
#  Registra las migraciones históricas ya aplicadas sin ejecutarlas.
#  Uso explícito: ./deploy/baseline-migrations.sh --confirm-production-baseline
###############################################################

set -euo pipefail

YELLOW='\033[1;33m'; GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
log()  { echo -e "${GREEN}[BASELINE]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
die()  { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

file_checksum() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        return 1
    fi
}

if [ "${1:-}" != "--confirm-production-baseline" ]; then
    die "Uso: $0 --confirm-production-baseline"
fi

APP_DIR="${APP_DIR:-/opt/sonograma/app}"
ENV_FILE="${ENV_FILE:-/etc/sonograma/sonograma.env}"
MIGRATION_DIR="$APP_DIR/docs/migraciones"
BASELINE_MAX_PREFIX=47
EXPECTED_BASELINE_COUNT=50

[ -f "$ENV_FILE" ] || die "Variables no encontradas: $ENV_FILE"
[ -d "$MIGRATION_DIR" ] || die "Directorio de migraciones no encontrado: $MIGRATION_DIR"
command -v docker >/dev/null 2>&1 || die "Docker no instalado"
file_checksum "$0" >/dev/null 2>&1 || die "No hay una herramienta SHA-256 disponible (sha256sum o shasum)"

set -a
source "$ENV_FILE"
set +a

if ! docker ps --format '{{.Names}}' | grep -qx sonograma-postgres; then
    die "PostgreSQL no está en ejecución; baseline cancelado."
fi

docker exec sonograma-postgres \
    psql -v ON_ERROR_STOP=1 \
    -U "${SPRING_DATASOURCE_USERNAME:-sonograma_user}" \
    -d sonograma_db \
    -c "CREATE TABLE IF NOT EXISTS sonograma_schema_migrations (
            filename VARCHAR(255) PRIMARY KEY,
            applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
            checksum VARCHAR(64) NOT NULL CHECK (checksum ~ '^[0-9a-f]{64}$')
        );"

BASELINE_COUNT=0
for MIGRATION in "$MIGRATION_DIR"/*.sql; do
    [ -f "$MIGRATION" ] || continue
    MIGRATION_NAME=$(basename "$MIGRATION")
    PREFIX="${MIGRATION_NAME%%_*}"
    if ! [[ "$PREFIX" =~ ^[0-9]{3}$ ]] || (( 10#$PREFIX > BASELINE_MAX_PREFIX )); then
        continue
    fi

    BASELINE_COUNT=$((BASELINE_COUNT + 1))
    CHECKSUM=$(file_checksum "$MIGRATION") || die "No se pudo calcular checksum para $MIGRATION_NAME"
    if ! LEDGER_CHECKSUM=$(docker exec -i sonograma-postgres \
        psql -Atq \
        -v "migration_filename=$MIGRATION_NAME" \
        -U "${SPRING_DATASOURCE_USERNAME:-sonograma_user}" \
        -d sonograma_db \
        <<'SQL'
SELECT checksum FROM sonograma_schema_migrations WHERE filename = :'migration_filename';
SQL
    ); then
        die "No se pudo consultar el ledger para $MIGRATION_NAME."
    fi

    if [ -n "$LEDGER_CHECKSUM" ]; then
        [ "$LEDGER_CHECKSUM" = "$CHECKSUM" ] || \
            die "Migration $MIGRATION_NAME has a different checksum in the ledger. Baseline aborted."
        log "Baseline ya registrado: $MIGRATION_NAME"
        continue
    fi

    docker exec -i sonograma-postgres \
        psql -v ON_ERROR_STOP=1 \
        -v "migration_filename=$MIGRATION_NAME" \
        -v "migration_checksum=$CHECKSUM" \
        -U "${SPRING_DATASOURCE_USERNAME:-sonograma_user}" \
        -d sonograma_db \
        <<'SQL'
INSERT INTO sonograma_schema_migrations (filename, checksum) VALUES (:'migration_filename', :'migration_checksum');
SQL
    log "Baseline registrado sin ejecutar SQL: $MIGRATION_NAME"
done

[ "$BASELINE_COUNT" -eq "$EXPECTED_BASELINE_COUNT" ] || \
    die "Se esperaban $EXPECTED_BASELINE_COUNT archivos de migración históricos hasta 047; se encontraron $BASELINE_COUNT."

log "Baseline completado: $BASELINE_COUNT migraciones registradas; ningún SQL histórico fue ejecutado."
