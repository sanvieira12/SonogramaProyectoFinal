#!/bin/bash
###############################################################
#  Sonograma – restore-db.sh
#  Restaura un backup .sql.gz. DESTRUYE datos actuales.
#  Uso: ./deploy/restore-db.sh /opt/sonograma/backups/archivo.sql.gz
###############################################################

set -euo pipefail

RED='\033[0;31m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'; NC='\033[0m'
log()  { echo -e "${GREEN}[RESTORE]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
die()  { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

ENV_FILE="/etc/sonograma/sonograma.env"
BACKUP_FILE="${1:-}"

[ -z "$BACKUP_FILE" ] && die "Uso: $0 <archivo_backup.sql.gz>"
[ -f "$BACKUP_FILE" ] || die "Archivo no encontrado: $BACKUP_FILE"
[ -f "$ENV_FILE" ]    || die "Variables no encontradas: $ENV_FILE"

set -a; source "$ENV_FILE"; set +a

DB_NAME="sonograma_db"
DB_USER="${SPRING_DATASOURCE_USERNAME:-sonograma_user}"

warn "=== RESTAURACIÓN DE BASE DE DATOS ==="
warn "Archivo: $BACKUP_FILE"
warn "⚠️  ADVERTENCIA: Reemplaza TODOS los datos actuales."
read -p "¿Confirmar? (escribe 'SI'): " CONFIRM
[ "$CONFIRM" = "SI" ] || die "Cancelado."

log "Backup de seguridad previo..."
/opt/sonograma/app/deploy/backup-db.sh || warn "No se pudo hacer backup previo."

log "Restaurando..."
if docker ps --format '{{.Names}}' | grep -q "sonograma-postgres"; then
    docker stop sonograma-backend 2>/dev/null || true
    docker exec sonograma-postgres psql -U "$DB_USER" -c "DROP DATABASE IF EXISTS ${DB_NAME};" postgres
    docker exec sonograma-postgres psql -U "$DB_USER" -c "CREATE DATABASE ${DB_NAME};" postgres
    gunzip < "$BACKUP_FILE" | docker exec -i sonograma-postgres psql -U "$DB_USER" -d "$DB_NAME"
    docker start sonograma-backend 2>/dev/null || \
        docker compose -f /opt/sonograma/app/docker-compose.prod.yml --env-file "$ENV_FILE" up -d backend
else
    export PGPASSWORD="${POSTGRES_PASSWORD}"
    dropdb -h localhost -U "$DB_USER" --if-exists "$DB_NAME"
    createdb -h localhost -U "$DB_USER" "$DB_NAME"
    gunzip < "$BACKUP_FILE" | psql -h localhost -U "$DB_USER" -d "$DB_NAME"
    unset PGPASSWORD
fi

log "=== Restauración exitosa ==="
