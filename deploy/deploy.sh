#!/bin/bash
###############################################################
#  Sonograma – deploy.sh
#  Deploy completo: backup → pull → build → restart → verify
#  Uso desde /opt/sonograma/app: ./deploy/deploy.sh
###############################################################

set -euo pipefail

YELLOW='\033[1;33m'; GREEN='\033[0;32m'; RED='\033[0;31m'; BLUE='\033[0;34m'; NC='\033[0m'
log()  { echo -e "${GREEN}[DEPLOY]${NC} $1"; }
step() { echo -e "${BLUE}[STEP]${NC} $1"; }
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

is_historical_migration() {
    local filename="$1"
    local prefix="${filename%%_*}"
    [[ "$prefix" =~ ^[0-9]{3}$ ]] || return 1
    (( 10#$prefix <= 47 ))
}

read_ledger_checksum() {
    local filename="$1"
    docker exec sonograma-postgres \
        psql -Atq \
        -v migration_filename="$filename" \
        -U "${SPRING_DATASOURCE_USERNAME:-sonograma_user}" \
        -d sonograma_db \
        -c "SELECT checksum FROM sonograma_schema_migrations WHERE filename = :'migration_filename';"
}

record_migration() {
    local filename="$1"
    local checksum="$2"
    docker exec sonograma-postgres \
        psql -v ON_ERROR_STOP=1 \
        -v migration_filename="$filename" \
        -v migration_checksum="$checksum" \
        -U "${SPRING_DATASOURCE_USERNAME:-sonograma_user}" \
        -d sonograma_db \
        -c "INSERT INTO sonograma_schema_migrations (filename, checksum) VALUES (:'migration_filename', :'migration_checksum');"
}

APP_DIR="${APP_DIR:-/opt/sonograma/app}"
ENV_FILE="${ENV_FILE:-/etc/sonograma/sonograma.env}"
DATA_DIR="${DATA_DIR:-/opt/sonograma/data}"
BRANCH="${BRANCH:-main}"

log "=== Sonograma – Deploy $(date '+%Y-%m-%d %H:%M:%S') ==="

[ -f "$ENV_FILE" ]     || die "Variables no encontradas: $ENV_FILE"
[ -d "$APP_DIR/.git" ] || die "Repo no clonado en $APP_DIR"
command -v docker &>/dev/null || die "Docker no instalado"
command -v node   &>/dev/null || die "Node.js no instalado"
command -v curl   &>/dev/null || die "curl no instalado"
file_checksum "$0" >/dev/null 2>&1 || die "No hay una herramienta SHA-256 disponible (sha256sum o shasum)"

set -a; source "$ENV_FILE"; set +a
mkdir -p "$DATA_DIR/vinylfuture-media" "$DATA_DIR/discogs-covers"

# 1. Backup previo
step "1/7 – Backup de base de datos..."
"$APP_DIR/deploy/backup-db.sh" || die "Backup falló; deploy cancelado sin modificar servicios."

# 2. Pull
step "2/7 – Actualizando código ($BRANCH)..."
cd "$APP_DIR"
git fetch origin
git checkout "$BRANCH"
git pull origin "$BRANCH"
log "Commit: $(git log --oneline -1)"

# Aplicar todas las migraciones pendientes antes del build (ddl-auto=validate las requiere).
# Esto incluye la normalización idempotente de categorías legacy de gasto_tienda.
if ! docker ps --format '{{.Names}}' | grep -qx sonograma-postgres; then
    die "PostgreSQL no está en ejecución; deploy cancelado antes de reemplazar servicios."
fi

if ! LEDGER_TABLE=$(docker exec sonograma-postgres \
    psql -Atq \
    -U "${SPRING_DATASOURCE_USERNAME:-sonograma_user}" \
    -d sonograma_db \
    -c "SELECT to_regclass('public.sonograma_schema_migrations');"); then
    die "No se pudo verificar el ledger de migraciones; deploy cancelado."
fi
[ "$LEDGER_TABLE" = "sonograma_schema_migrations" ] || \
    die "No existe el ledger de migraciones; ejecute baseline-migrations.sh antes del deploy."

BASELINE_COUNT=0
for MIGRATION in "$APP_DIR"/docs/migraciones/*.sql; do
    [ -f "$MIGRATION" ] || continue
    MIGRATION_NAME=$(basename "$MIGRATION")
    if is_historical_migration "$MIGRATION_NAME"; then
        BASELINE_COUNT=$((BASELINE_COUNT + 1))
        CHECKSUM=$(file_checksum "$MIGRATION") || die "No se pudo calcular checksum para $MIGRATION_NAME"
        if ! LEDGER_CHECKSUM=$(read_ledger_checksum "$MIGRATION_NAME"); then
            die "No se pudo consultar el ledger para $MIGRATION_NAME; deploy cancelado."
        fi
        [ -n "$LEDGER_CHECKSUM" ] || \
            die "Falta el baseline de $MIGRATION_NAME; ejecute baseline-migrations.sh antes del deploy."
        [ "$LEDGER_CHECKSUM" = "$CHECKSUM" ] || \
            die "Migration $MIGRATION_NAME was previously applied with a different checksum. Deployment aborted."
    fi
done
[ "$BASELINE_COUNT" -eq 50 ] || \
    die "Se esperaban 50 archivos de migración históricos hasta 047; se encontraron $BASELINE_COUNT."

for MIGRATION in "$APP_DIR"/docs/migraciones/*.sql; do
    [ -f "$MIGRATION" ] || continue
    MIGRATION_NAME=$(basename "$MIGRATION")
    CHECKSUM=$(file_checksum "$MIGRATION") || die "No se pudo calcular checksum para $MIGRATION_NAME"
    if ! LEDGER_CHECKSUM=$(read_ledger_checksum "$MIGRATION_NAME"); then
        die "No se pudo consultar el ledger para $MIGRATION_NAME; deploy cancelado."
    fi

    if [ -n "$LEDGER_CHECKSUM" ]; then
        [ "$LEDGER_CHECKSUM" = "$CHECKSUM" ] || \
            die "Migration $MIGRATION_NAME was previously applied with a different checksum. Deployment aborted."
        log "Migración ya aplicada, checksum verificado: $MIGRATION_NAME"
        continue
    fi

    step "Aplicando migración nueva: $MIGRATION_NAME..."
    if ! docker exec -i sonograma-postgres \
        psql -v ON_ERROR_STOP=1 \
        -U "${SPRING_DATASOURCE_USERNAME:-sonograma_user}" \
        -d sonograma_db < "$MIGRATION"; then
        die "Migration $MIGRATION_NAME failed. Deployment aborted."
    fi

    if ! record_migration "$MIGRATION_NAME" "$CHECKSUM"; then
        die "Migration $MIGRATION_NAME succeeded but could not be recorded. Deployment aborted."
    fi
    log "Migración registrada: $MIGRATION_NAME"
done

LEGACY_EXPENSES=$(docker exec sonograma-postgres \
    psql -Atq \
    -U "${SPRING_DATASOURCE_USERNAME:-sonograma_user}" \
    -d sonograma_db \
    -c "SELECT COUNT(*) FROM gasto_tienda WHERE categoria IS NOT NULL AND LOWER(BTRIM(categoria)) IN ('gastos del local', 'gasto local', 'gastos de tienda');")
[ "$LEGACY_EXPENSES" = "0" ] || die "Quedaron $LEGACY_EXPENSES gastos con categorías legacy sin normalizar"

# 3. Build frontend
# Nota: el frontend usa VITE_API_URL (ver frontend/src/api/sonograma.js)
step "3/7 – Build frontend React..."
cd "$APP_DIR/frontend"
cat > .env.production <<ENVFILE
VITE_API_URL=/api
ENVFILE
export VITE_API_URL=/api
log "VITE_API_URL=/api (mismo origen, Nginx enruta al backend)"
npm ci --prefer-offline 2>/dev/null || npm install
npm run build
log "Frontend compilado en frontend/dist"

cd "$APP_DIR"

# 4. Build backend Docker
step "4/7 – Build imagen Docker backend..."
docker compose -f docker-compose.prod.yml build backend
log "Imagen backend construida"

# 5. Restart servicios
step "5/7 – Reiniciando servicios..."
docker compose -f docker-compose.prod.yml stop backend nginx 2>/dev/null || true
docker compose -f docker-compose.prod.yml --env-file "$ENV_FILE" up -d --remove-orphans

# 6. Healthcheck
step "6/7 – Esperando que el backend esté saludable..."
MAX_WAIT=120; WAIT=0
until docker exec sonograma-backend \
    curl -sf http://localhost:8080/api/actuator/health > /dev/null 2>&1; do
    sleep 5; WAIT=$((WAIT + 5))
    if [ $WAIT -ge $MAX_WAIT ]; then
        docker logs sonograma-backend --tail 50
        die "Backend no saludable después de ${MAX_WAIT}s"
    fi
    log "Esperando... (${WAIT}s)"
done
log "Backend saludable ✓"

# 7. Verificación final
step "7/7 – Verificación final..."
docker compose -f docker-compose.prod.yml ps
echo ""
docker exec sonograma-backend \
    curl -s http://localhost:8080/api/actuator/health | python3 -m json.tool 2>/dev/null \
    || docker exec sonograma-backend curl -s http://localhost:8080/api/actuator/health
echo ""

if ! docker exec sonograma-nginx nginx -t >/dev/null 2>&1; then
    docker logs sonograma-nginx --tail 50
    die "Nginx no saludable; deploy incompleto."
fi

if ! curl -kfsS --resolve tiendasonograma.com:443:127.0.0.1 \
    https://tiendasonograma.com/api/actuator/health >/dev/null; then
    die "Nginx/aplicación no saludable; deploy incompleto."
fi
log "Nginx y aplicación saludables ✓"
log "=== Deploy completado ==="
log "Frontend:  http://localhost"
log "API:       http://localhost/api/actuator/health"
log "Logs:      docker logs sonograma-backend -f"
