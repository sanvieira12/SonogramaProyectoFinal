#!/bin/bash
###############################################################
#  Sonograma – backup-db.sh
#  Backup automático de PostgreSQL con retención de 14 días.
#  Cron: 0 3 * * * /opt/sonograma/app/deploy/backup-db.sh
###############################################################

set -euo pipefail

ENV_FILE="/etc/sonograma/sonograma.env"
BACKUP_DIR="/opt/sonograma/backups"
TIMESTAMP=$(date '+%Y%m%d_%H%M%S')
BACKUP_FILE="$BACKUP_DIR/sonograma_db_${TIMESTAMP}.sql.gz"
KEEP_DAYS=14

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log()  { echo -e "${GREEN}[BACKUP]${NC} $(date '+%H:%M:%S') $1"; }
die()  { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

log "=== Backup Sonograma DB ==="

[ -f "$ENV_FILE" ] || die "No se encontró $ENV_FILE"
set -a; source "$ENV_FILE"; set +a

DB_NAME="sonograma_db"
DB_USER="${SPRING_DATASOURCE_USERNAME:-sonograma_user}"

mkdir -p "$BACKUP_DIR"

if docker ps --format '{{.Names}}' | grep -q "sonograma-postgres"; then
    log "Backup via Docker..."
    docker exec sonograma-postgres \
        pg_dump -U "$DB_USER" -d "$DB_NAME" --no-password \
        | gzip > "$BACKUP_FILE"
else
    log "Backup local..."
    export PGPASSWORD="${POSTGRES_PASSWORD}"
    pg_dump -h localhost -U "$DB_USER" -d "$DB_NAME" | gzip > "$BACKUP_FILE"
    unset PGPASSWORD
fi

BACKUP_SIZE=$(du -sh "$BACKUP_FILE" | cut -f1)
log "Backup creado: $BACKUP_FILE ($BACKUP_SIZE)"

find "$BACKUP_DIR" -name "sonograma_db_*.sql.gz" -mtime "+${KEEP_DAYS}" -delete
BACKUP_COUNT=$(find "$BACKUP_DIR" -name "sonograma_db_*.sql.gz" | wc -l)
log "Backups disponibles: $BACKUP_COUNT"
ls -lh "$BACKUP_DIR"/sonograma_db_*.sql.gz 2>/dev/null || true
log "=== Backup completado ==="
