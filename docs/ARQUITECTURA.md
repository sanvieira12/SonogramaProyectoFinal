# Arquitectura del Sistema Sonograma

## Capas de la Aplicación

```
Frontend (React + Vite)
        ↕ HTTP/JSON (REST API)
Backend (Spring Boot)
        ↕ JPA/JDBC
Base de Datos (PostgreSQL)
```

### Frontend — `frontend/`
- **React + Vite**: SPA con hot-reload en desarrollo.
- **Tailwind CSS**: Utilidades CSS sin CSS personalizado manual.
- **Servicios (`src/services/`)**: Módulos que encapsulan las llamadas HTTP al backend.
- **Hooks (`src/hooks/`)**: Estado compartido entre componentes.

### Backend — `sonograma-backend/`
| Capa | Paquete | Responsabilidad |
|---|---|---|
| Controller | `controller/` | Recibe requests HTTP, valida entrada, devuelve respuestas |
| Service | `service/` | Lógica de negocio, transacciones |
| Repository | `repository/` | Acceso a datos vía JPA |
| Entity | `entity/` | Modelo de dominio mapeado a tablas |
| DTO | `dto/` | Objetos de transferencia para requests/responses |
| Exception | `exception/` | Manejo centralizado de errores |

### Base de Datos
- **PostgreSQL 16** corriendo en Docker.
- JPA con `ddl-auto=update` en dev (crea y actualiza tablas automáticamente).
- En producción usar `validate` + migraciones Flyway.

## Flujo de Datos

```
Usuario → Navegador → React (Component)
        → Service (fetch)
        → Spring Controller (@RestController)
        → Service (@Service + @Transactional)
        → Repository (JpaRepository)
        → PostgreSQL
```

## Entidades Principales

- **Usuario**: Operadores del sistema (integración futura con AWS Cognito).
- **Cliente**: Clientes de la disquería.
- **DireccionCliente**: Direcciones reutilizables asociadas a clientes para envíos.
- **Disco**: Stock de vinilos/CDs.
- **Venta**: Transacciones de venta.
- **Envio**: Datos logísticos de ventas con envío, incluyendo preparación para DAC.
- **Reserva**: Reservas de discos con seña opcional.
