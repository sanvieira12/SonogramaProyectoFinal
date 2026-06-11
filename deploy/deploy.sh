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

APP_DIR="/opt/sonograma/app"
ENV_FILE="/etc/sonograma/sonograma.env"
BRANCH="${BRANCH:-main}"

log "=== Sonograma – Deploy $(date '+%Y-%m-%d %H:%M:%S') ==="

[ -f "$ENV_FILE" ]     || die "Variables no encontradas: $ENV_FILE"
[ -d "$APP_DIR/.git" ] || die "Repo no clonado en $APP_DIR"
command -v docker &>/dev/null || die "Docker no instalado"
command -v node   &>/dev/null || die "Node.js no instalado"

set -a; source "$ENV_FILE"; set +a

# 1. Backup previo
step "1/7 – Backup de base de datos..."
"$APP_DIR/deploy/backup-db.sh" || warn "Backup falló, continuando con precaución..."

# 2. Pull
step "2/7 – Actualizando código ($BRANCH)..."
cd "$APP_DIR"
git fetch origin
git checkout "$BRANCH"
git pull origin "$BRANCH"
log "Commit: $(git log --oneline -1)"

# 3. Build frontend
# Nota: el frontend usa VITE_API_URL (ver frontend/src/api/sonograma.js)
step "3/7 – Build frontend React..."
cd "$APP_DIR/frontend"
cat > .env.production <<ENVFILE
VITE_API_URL=${SONOGRAMA_FRONTEND_BASE_URL}/api
ENVFILE
log "VITE_API_URL=${SONOGRAMA_FRONTEND_BASE_URL}/api"
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
log "=== Deploy completado ==="
log "Frontend:  http://localhost"
log "API:       http://localhost/api/actuator/health"
log "Logs:      docker logs sonograma-backend -f"
