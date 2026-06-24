#!/bin/bash
###############################################################
#  Sonograma – setup-server.sh
#  Instala todas las dependencias en Ubuntu LTS limpio.
#  Ejecutar UNA SOLA VEZ en el servidor Lightsail como root.
#  Uso: sudo ./deploy/setup-server.sh
###############################################################

set -euo pipefail

YELLOW='\033[1;33m'; GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
log()  { echo -e "${GREEN}[SETUP]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
die()  { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

log "=== Sonograma – Setup servidor Lightsail ==="
log "Fecha: $(date)"

[[ $EUID -ne 0 ]] && die "Ejecutar como root: sudo ./setup-server.sh"

# 1. Actualizar sistema
log "Actualizando paquetes..."
apt-get update -qq
apt-get upgrade -y -qq
apt-get install -y -qq \
    curl wget git unzip zip \
    ca-certificates gnupg lsb-release \
    software-properties-common apt-transport-https \
    ufw fail2ban htop ncdu jq

# 2. Docker Engine + Compose Plugin
if ! command -v docker &>/dev/null; then
    log "Instalando Docker..."
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
        | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
        https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
        > /etc/apt/sources.list.d/docker.list
    apt-get update -qq
    apt-get install -y docker-ce docker-ce-cli containerd.io \
        docker-buildx-plugin docker-compose-plugin
    systemctl enable --now docker
    usermod -aG docker ubuntu 2>/dev/null || true
    log "Docker instalado: $(docker --version)"
else
    log "Docker ya instalado: $(docker --version)"
fi

# 3. Node.js LTS
if ! command -v node &>/dev/null; then
    log "Instalando Node.js LTS..."
    curl -fsSL https://deb.nodesource.com/setup_lts.x | bash -
    apt-get install -y nodejs
    log "Node.js: $(node --version)"
else
    log "Node.js ya instalado: $(node --version)"
fi

# 4. Certbot
if ! command -v certbot &>/dev/null; then
    apt-get install -y certbot python3-certbot-nginx
fi

# 5. Directorios
log "Creando estructura de directorios..."
mkdir -p /opt/sonograma/backups /opt/sonograma/logs /opt/sonograma/app /opt/sonograma/data/vinylfuture-media /etc/sonograma
chown -R ubuntu:ubuntu /opt/sonograma 2>/dev/null || \
    chown -R $SUDO_USER:$SUDO_USER /opt/sonograma 2>/dev/null || true
chmod -R 755 /opt/sonograma

# 6. Archivo de variables de entorno
if [ ! -f /etc/sonograma/sonograma.env ]; then
    log "Creando plantilla de variables..."
    cat > /etc/sonograma/sonograma.env <<'ENV'
# Sonograma – Variables de entorno de producción
# EDITAR con valores reales antes del primer deploy

SPRING_DATASOURCE_USERNAME=sonograma_user
POSTGRES_PASSWORD=CAMBIAR_POR_PASSWORD_SEGURA

# Generar con: openssl rand -hex 32
JWT_SECRET=CAMBIAR_POR_SECRETO_JWT_MINIMO_32_CARACTERES
JWT_EXPIRATION=86400000

SONOGRAMA_FRONTEND_BASE_URL=https://tudominio.com

DISCOGS_TOKEN=
COMPOSE_PROJECT_NAME=sonograma
ENV
    chmod 600 /etc/sonograma/sonograma.env
    warn "IMPORTANTE: Editar /etc/sonograma/sonograma.env con valores reales!"
fi

# 7. Firewall
log "Configurando firewall UFW..."
ufw --force reset
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp   comment "SSH"
ufw allow 80/tcp   comment "HTTP"
ufw allow 443/tcp  comment "HTTPS"
ufw --force enable

# 8. Fail2ban
cat > /etc/fail2ban/jail.local <<'F2B'
[sshd]
enabled  = true
port     = ssh
maxretry = 5
bantime  = 1h
F2B
systemctl enable --now fail2ban

# 9. Swap (importante para plan $10 con 2GB RAM)
if [ ! -f /swapfile ]; then
    log "Creando swapfile 2GB..."
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
    sysctl vm.swappiness=10
    echo 'vm.swappiness=10' >> /etc/sysctl.conf
fi

# 10. Cron de backup diario a las 03:00
CRON_JOB="0 3 * * * /opt/sonograma/app/deploy/backup-db.sh >> /opt/sonograma/logs/backup.log 2>&1"
(crontab -u ubuntu -l 2>/dev/null | grep -v backup-db.sh; echo "$CRON_JOB") \
    | crontab -u ubuntu - 2>/dev/null || \
    (crontab -l 2>/dev/null | grep -v backup-db.sh; echo "$CRON_JOB") \
    | crontab -

log ""
log "=== Setup completado ==="
log "Próximos pasos:"
log "  1. Editar /etc/sonograma/sonograma.env"
log "  2. Clonar el repo en /opt/sonograma/app"
log "  3. Ejecutar ./deploy/deploy.sh"
warn "Hacer logout y login para que el grupo 'docker' tome efecto."
