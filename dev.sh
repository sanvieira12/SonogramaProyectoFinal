#!/bin/bash
set -e

echo ""
echo "╔══════════════════════════════════════╗"
echo "║   Sonograma — Entorno local (DEV)    ║"
echo "╚══════════════════════════════════════╝"
echo ""

# 1. Levantar PostgreSQL local con Docker
echo "▶ Levantando PostgreSQL local..."
docker compose -f sonograma-backend/docker-compose.yml up -d

# Esperar a que Postgres esté listo
echo "  Esperando que PostgreSQL esté disponible..."
sleep 3

# 2. Backend
echo ""
echo "▶ Iniciando backend Spring Boot (localhost:8080)..."
cd sonograma-backend
mvn spring-boot:run -q &
BACKEND_PID=$!
cd ..

# Dar tiempo al backend para arrancar
echo "  Esperando que el backend esté disponible..."
for i in $(seq 1 30); do
  if curl -s http://localhost:8080/api/actuator/health > /dev/null 2>&1; then
    echo "  ✓ Backend listo"
    break
  fi
  sleep 2
done

# 3. Frontend
echo ""
echo "▶ Iniciando frontend Vite (localhost:5173)..."
cd frontend
npm run dev &
FRONTEND_PID=$!
cd ..

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║  ✓ Todo corriendo en LOCAL                       ║"
echo "║                                                  ║"
echo "║  Frontend:  http://localhost:5173                ║"
echo "║  Backend:   http://localhost:8080/api            ║"
echo "║  pgAdmin:   http://localhost:5050                ║"
echo "║             usuario: admin@sonograma.com         ║"
echo "║             contraseña: admin123                 ║"
echo "╚══════════════════════════════════════════════════╝"
echo ""
echo "  Presioná Ctrl+C para detener todo."
echo ""

trap "echo ''; echo 'Deteniendo...'; kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; docker compose -f sonograma-backend/docker-compose.yml stop; exit 0" INT TERM
wait
