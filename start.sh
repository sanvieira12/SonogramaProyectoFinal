#!/bin/bash
echo "Iniciando Sonograma..."

echo "Verificando Docker (PostgreSQL)..."
docker compose -f sonograma-backend/docker-compose.yml up -d

echo "Iniciando backend..."
cd sonograma-backend && mvn spring-boot:run &
BACKEND_PID=$!

echo "Iniciando frontend..."
cd frontend && npm run dev &
FRONTEND_PID=$!

echo ""
echo "Backend:  http://localhost:8080/api"
echo "Frontend: http://localhost:5173"
echo "pgAdmin:  http://localhost:5050"
echo ""
echo "Presiona Ctrl+C para detener todo"

trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit" INT TERM
wait
