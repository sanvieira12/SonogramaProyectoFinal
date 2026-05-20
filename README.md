# Sonograma — Sistema de Gestión de Disquería

Sistema web para gestión de stock, ventas y clientes de Sonograma.

## Stack

| Capa | Tecnología |
|---|---|
| Frontend | React + Vite + Tailwind CSS |
| Backend | Java 21 + Spring Boot 3.2 |
| Base de datos | PostgreSQL 16 |
| Identidad | AWS Cognito (próximo sprint) |

## Inicio rápido

### 1. Levantar base de datos
```bash
docker compose -f sonograma-backend/docker-compose.yml up -d
```

### 2. Backend
```bash
cd sonograma-backend
mvn spring-boot:run
# → http://localhost:8080/api
```

### 3. Frontend
```bash
cd frontend
npm run dev
# → http://localhost:5173
```

### 4. Todo junto
```bash
chmod +x start.sh
./start.sh
```

## pgAdmin
- URL: http://localhost:5050
- Email: `admin@sonograma.com`
- Password: `admin123`
- Servidor: host `postgres`, port `5432`, user `sonograma_user`, pass `sonograma_pass`

## Estructura
```
sonograma/
├── sonograma-backend/   Spring Boot API
├── frontend/            React + Vite
├── docs/                Arquitectura y decisiones
├── start.sh             Script de inicio
└── README.md
```

## Documentación
- [Arquitectura](docs/ARQUITECTURA.md)
- [Decisiones técnicas](docs/DECISIONS.md)
- [Diagrama ER](docs/ER_DIAGRAM.md)
