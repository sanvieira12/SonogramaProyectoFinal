# Deploy de Sonograma

## Arquitectura de producción

| Servicio | Provider | URL |
|---|---|---|
| Frontend | Vercel | https://sonograma.vercel.app |
| Backend | Railway | https://sonograma-backend.up.railway.app |
| PostgreSQL | Railway | servicio interno (mismo proyecto) |

---

## Variables de entorno

### Backend (Railway)

| Variable | Ejemplo | Notas |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | Activa `application-prod.properties` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://host:port/db` | Railway la genera automáticamente desde el servicio PG |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Inyectada por Railway |
| `SPRING_DATASOURCE_PASSWORD` | `(secret)` | Inyectada por Railway |
| `JWT_SECRET` | `(64+ chars aleatorios)` | Generar con `openssl rand -base64 64` |
| `CORS_ALLOWED_ORIGINS` | `https://sonograma.vercel.app` | Separar por comas si hay varios orígenes |
| `PORT` | `(automático)` | Railway lo setea solo, no configurar manualmente |

> **Cómo conectar la DB de Railway al backend:**
> En el servicio de Railway del backend, ir a Variables → agregar las referencias del servicio PostgreSQL.
> Railway permite referenciar variables de otro servicio con la sintaxis `${{Postgres.DATABASE_URL}}`.
> Mapearlas así:
> - `SPRING_DATASOURCE_URL` → convertir el `postgresql://user:pass@host:port/db` a formato JDBC: `jdbc:postgresql://host:port/db`
> - `SPRING_DATASOURCE_USERNAME` → `${{Postgres.PGUSER}}`
> - `SPRING_DATASOURCE_PASSWORD` → `${{Postgres.PGPASSWORD}}`

### Frontend (Vercel)

| Variable | Ejemplo |
|---|---|
| `VITE_API_URL` | `https://sonograma-backend.up.railway.app/api` |

> En Vercel: Settings → Environment Variables → agregar `VITE_API_URL` con la URL real del backend en Railway.
> El build de Vercel embebe la variable en el JS estático, por eso debe estar seteada **antes** del deploy.

---

## Pasos para el primer deploy

### 1. Backend en Railway

1. Crear nuevo proyecto en [railway.app](https://railway.app)
2. Agregar servicio PostgreSQL → Railway crea la DB y expone las variables
3. Agregar servicio desde GitHub → seleccionar el repo `SonogramaProyectoFinal`, rama `main`
4. Railway detecta `railway.json` y usa el `Dockerfile` de `sonograma-backend/`
5. En Variables del servicio backend, setear:
   - `SPRING_PROFILES_ACTIVE=prod`
   - `SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:<port>/<db>`
   - `SPRING_DATASOURCE_USERNAME=<usuario>`
   - `SPRING_DATASOURCE_PASSWORD=<password>`
   - `JWT_SECRET=<clave generada con openssl>`
   - `CORS_ALLOWED_ORIGINS=https://<tu-app>.vercel.app`
6. Deploy automático. Verificar en `/api/actuator/health` → `{"status":"UP"}`

### 2. Frontend en Vercel

1. Importar el repo en [vercel.com](https://vercel.com)
2. Seleccionar **Root Directory** → `frontend`
3. Framework preset → **Vite** (o auto-detectado)
4. En Environment Variables, agregar:
   - `VITE_API_URL=https://<tu-backend>.up.railway.app/api`
5. Deploy. El `vercel.json` ya configura el SPA routing.

---

## Cómo cargar el seed en producción

Una vez el backend está deployado y la DB creada (las tablas las genera Hibernate con `ddl-auto=update`):

**Opción A — Railway CLI:**
```bash
# Instalar Railway CLI
npm install -g @railway/cli

# Login
railway login

# Conectar al proyecto
railway link

# Conectar a la DB
railway connect postgres

# Dentro del psql de Railway, ejecutar los seeds:
\i sonograma-backend/src/main/resources/db/seed_discos_deejay_abril2026.sql
\i sonograma-backend/src/main/resources/db/seed_clientes_ventas.sql
```

**Opción B — psql externo con URL de conexión:**
```bash
# Railway expone una URL de conexión externa en el panel
psql "postgresql://user:pass@host:port/db?sslmode=require" \
  -f sonograma-backend/src/main/resources/db/seed_discos_deejay_abril2026.sql

psql "postgresql://user:pass@host:port/db?sslmode=require" \
  -f sonograma-backend/src/main/resources/db/seed_clientes_ventas.sql
```

---

## Rollback

- **Vercel**: Dashboard → proyecto → Deployments → click en deploy anterior → "Promote to Production"
- **Railway**: Dashboard → servicio → Deployments → click en deploy anterior → "Redeploy"

---

## Desarrollo local

El proyecto sigue funcionando en local sin cambios:

```bash
# Backend (usa application.properties con perfil default → DB en Docker)
cd sonograma-backend
mvn spring-boot:run

# Frontend (usa proxy /api → localhost:8080)
cd frontend
npm run dev
```

La variable `SPRING_PROFILES_ACTIVE` no está seteada localmente, por lo que usa el perfil `default` con la configuración de `application.properties` (Docker PostgreSQL en localhost:5432).
