# Sonograma – Deploy en AWS Lightsail

## 1. Instancia recomendada

| Campo | Valor |
|---|---|
| Proveedor | AWS Lightsail |
| OS | Ubuntu 22.04 LTS |
| Plan | $10/mes – 2 GB RAM, 1 vCPU, 60 GB SSD |
| IP | Estática (asignar en el panel de Lightsail) |

> El plan de $10 es suficiente para esta app de gestión interna. El swapfile de 2 GB que crea `setup-server.sh` cubre picos de memoria durante el build.

---

## 2. Arquitectura

```
Internet
   │
   ▼
[Nginx :80/:443]   ← sirve React (dist/) + proxy a /api
   │
   ├──── /api/*  ──────▶  [Spring Boot :8080]  ──▶  [PostgreSQL :5432]
   │                             │
   │                      SPRING_PROFILES_ACTIVE=prod
   │                      application-prod.properties
   │
   └──── /*  ────────────  /var/www/sonograma  (frontend/dist/)

Volúmenes Docker:
  postgres_data  →  datos DB (persistente)
  logs_data      →  /opt/sonograma/logs (bind mount)
Backups:           /opt/sonograma/backups  (cron 03:00 diario)
```

---

## 3. Variables de entorno requeridas

Archivo: `/etc/sonograma/sonograma.env` (permisos 600)

| Variable | Descripción | Ejemplo |
|---|---|---|
| `SPRING_DATASOURCE_USERNAME` | Usuario de PostgreSQL | `sonograma_user` |
| `POSTGRES_PASSWORD` | Password de PostgreSQL | `s3cr3t0_muy_largo` |
| `JWT_SECRET` | Secreto JWT (mín. 32 chars) | `openssl rand -hex 32` |
| `JWT_EXPIRATION` | Expiración token en ms | `86400000` (24h) |
| `SONOGRAMA_FRONTEND_BASE_URL` | URL canónica usada en enlaces generados | `https://tiendasonograma.com` |
| `SONOGRAMA_CORS_ALLOWED_ORIGINS` | Orígenes web permitidos, separados por comas | `https://tiendasonograma.com,https://www.tiendasonograma.com` |
| `GOOGLE_CLIENT_ID` | Client ID del cliente OAuth Web de Google | _(secreto operativo; no commitear)_ |
| `GOOGLE_CLIENT_SECRET` | Client secret del cliente OAuth Web de Google | _(secreto; no commitear)_ |
| `SONOGRAMA_GOOGLE_ADMIN_EMAIL` | Única cuenta Google autorizada | `sonograma.tiendadediscos@gmail.com` |
| `SONOGRAMA_GOOGLE_REDIRECT_URI` | Callback OAuth exacta | `https://tiendasonograma.com/api/login/oauth2/code/google` |
| `DISCOGS_TOKEN` | Token API Discogs (opcional) | _(vacío si no se usa)_ |

---

## 4. Primer deploy (paso a paso)

### 4.1 Crear la instancia
1. Panel Lightsail → **Create instance** → Linux/Unix → Ubuntu 22.04 LTS → $10/mes
2. En **Networking** → crear y asignar una **Static IP**
3. Abrir puertos en el firewall de Lightsail: **22, 80, 443** (ver sección 14)

### 4.2 Conectarse por SSH
```bash
ssh -i ~/.ssh/LightsailDefaultKey.pem ubuntu@<IP-ESTATICA>
```

### 4.3 Clonar el repo y ejecutar setup
```bash
cd /opt/sonograma
git clone https://github.com/tu-usuario/sonograma.git app
cd app
sudo ./deploy/setup-server.sh
```

### 4.4 Configurar variables de entorno
```bash
sudo nano /etc/sonograma/sonograma.env
# Completar todos los valores. Guardar con Ctrl+O, salir con Ctrl+X.
```

Generar el JWT_SECRET:
```bash
openssl rand -hex 32
```

### 4.5 Primer deploy
```bash
# Hacer logout y login para que el grupo 'docker' tome efecto
exit
ssh -i ~/.ssh/LightsailDefaultKey.pem ubuntu@<IP-ESTATICA>

cd /opt/sonograma/app
./deploy/deploy.sh
```

---

## 5. Actualizar el proyecto

```bash
cd /opt/sonograma/app
./deploy/deploy.sh
```

El script hace automáticamente: backup DB → git pull → verificar PostgreSQL y el ledger → validar checksums → aplicar solo migraciones nuevas con `ON_ERROR_STOP=1` → build frontend → build Docker → restart → healthcheck. Si PostgreSQL, el ledger o una migración no pasan la validación, el deploy se aborta antes de compilar o reemplazar servicios; una migración fallida no se trata como "ya aplicada".

### Primera actualización después del tracking de migraciones

La producción verificada ya contiene el esquema y los resultados de las migraciones `001` a `047`. No se deben volver a ejecutar esas migraciones para poblar el ledger.

Ejecutar la siguiente secuencia deliberada en el servidor:

1. **Verificación read-only de producción**: confirmar el checkout `/opt/sonograma/app`, el contenedor `sonograma-postgres` y que el esquema corresponde al estado verificado hasta `047`. No ejecutar SQL de las migraciones.
2. **Backup fresco**:
   ```bash
   /opt/sonograma/app/deploy/backup-db.sh
   ```
3. **Baseline explícito, sin ejecutar migraciones históricas**:
   ```bash
   cd /opt/sonograma/app
   ./deploy/baseline-migrations.sh --confirm-production-baseline
   ```
   El script registra los nombres completos y checksums SHA-256 de los 50 archivos con prefijo `001`–`047` en `sonograma_schema_migrations`. Si encuentra un checksum distinto, se detiene.
4. **Verificar el ledger** con una consulta SELECT-only:
   ```bash
   set -a; source /etc/sonograma/sonograma.env; set +a
   docker exec sonograma-postgres psql -Atq \
       -U "${SPRING_DATASOURCE_USERNAME:-sonograma_user}" \
       -d sonograma_db \
       -c "SELECT COUNT(*), MIN(filename), MAX(filename) FROM sonograma_schema_migrations;"
   ```
   Deben existir 50 filas históricas (hay prefijos duplicados `010`, `021` y `025`) y los checksums deben coincidir con el checkout desplegado.
5. **Deploy normal**:
   ```bash
   ./deploy/deploy.sh
   ```
   El deploy valida el baseline y omite los 50 archivos `001`–`047`; solo ejecuta un archivo nuevo ausente del ledger, y lo registra después de que el SQL termina correctamente.
6. **Health checks**: confirmar backend `/api/actuator/health`, Nginx y la URL pública de la aplicación. Un fallo detiene el script con código no cero.

El ledger usa el nombre de archivo completo porque existen prefijos duplicados como `010`, `021` y `025`. Los archivos históricos no deben renombrarse ni modificarse; un cambio de checksum aborta el deploy.

Para el deploy acotado de Google OAuth, configuración de Google Cloud, pruebas y rollback sin tocar PostgreSQL, seguir [docs/GOOGLE_AUTH.md](../docs/GOOGLE_AUTH.md).

Para forzar una rama específica:
```bash
BRANCH=develop ./deploy/deploy.sh
```

---

## 6. Ver logs

```bash
# Backend (tiempo real)
docker logs sonograma-backend -f

# Últimas 100 líneas
docker logs sonograma-backend --tail 100

# Log en archivo (rotado automáticamente)
tail -f /opt/sonograma/logs/backend.log

# Nginx
docker logs sonograma-nginx -f

# Todos los contenedores
docker compose -f /opt/sonograma/app/docker-compose.prod.yml logs -f
```

---

## 7. Reiniciar servicios

```bash
cd /opt/sonograma/app

# Reiniciar todo
docker compose -f docker-compose.prod.yml restart

# Reiniciar solo backend
docker compose -f docker-compose.prod.yml restart backend

# Reiniciar solo nginx
docker compose -f docker-compose.prod.yml restart nginx

# Ver estado
docker compose -f docker-compose.prod.yml ps
```

---

## 8. Backups

### Automático
El `setup-server.sh` configura un cron que ejecuta `backup-db.sh` todos los días a las 03:00.
- Retención: 14 días
- Ubicación: `/opt/sonograma/backups/sonograma_db_YYYYMMDD_HHMMSS.sql.gz`
- Log: `/opt/sonograma/logs/backup.log`

### Manual
```bash
/opt/sonograma/app/deploy/backup-db.sh
```

### Listar backups disponibles
```bash
ls -lh /opt/sonograma/backups/
```

---

## 9. Restaurar backup

```bash
./deploy/restore-db.sh /opt/sonograma/backups/sonograma_db_20260101_030000.sql.gz
```

El script pide confirmación explícita (`SI`) antes de destruir datos. Hace un backup de seguridad previo automáticamente.

---

## 10. Configurar dominio

1. En tu registrador de dominios (o Route 53), crear un registro **A**:
   - Nombre: `tudominio.com` → IP estática de Lightsail
   - Nombre: `www.tudominio.com` → misma IP
2. Actualizar `server_name` en `deploy/nginx-sonograma.conf`
3. Actualizar `SONOGRAMA_FRONTEND_BASE_URL` y `SONOGRAMA_CORS_ALLOWED_ORIGINS` en `/etc/sonograma/sonograma.env`
4. Reiniciar nginx: `docker compose -f docker-compose.prod.yml restart nginx`

---

## 11. Activar HTTPS con Certbot

Primero, asegurarse de que el dominio apunta correctamente a la IP y que el puerto 80 está abierto.

```bash
# Detener nginx temporalmente para liberar el puerto 80
docker compose -f /opt/sonograma/app/docker-compose.prod.yml stop nginx

# Obtener certificado
sudo certbot certonly --standalone -d tudominio.com -d www.tudominio.com

# Volver a levantar nginx
docker compose -f /opt/sonograma/app/docker-compose.prod.yml start nginx
```

Luego en `deploy/nginx-sonograma.conf`:
1. Descomentar `return 301 https://$host$request_uri;` en el bloque HTTP
2. Comentar los `location` del bloque HTTP (excepto el de certbot)
3. Descomentar el bloque `server { listen 443 ... }` completo
4. Reemplazar `tudominio.com` por tu dominio real

```bash
docker compose -f /opt/sonograma/app/docker-compose.prod.yml restart nginx
```

Para que la renovación automática con el modo `standalone` pueda usar el puerto 80,
instalar los hooks versionados que detienen Nginx únicamente durante la renovación y
lo vuelven a iniciar si estaba activo:

```bash
sudo install -m 755 deploy/certbot-renewal-pre.sh \
    /etc/letsencrypt/renewal-hooks/pre/10-stop-sonograma-nginx
sudo install -m 755 deploy/certbot-renewal-post.sh \
    /etc/letsencrypt/renewal-hooks/post/90-start-sonograma-nginx
sudo certbot renew --dry-run
```

Certbot instala su temporizador de renovación con `python3-certbot-nginx`; los hooks
no contienen dominios, credenciales ni secretos.

---

## 12. Migrar datos desde Railway

### En tu máquina local (con acceso a Railway):
```bash
# Obtener la DATABASE_URL de Railway
railway variables

# Hacer dump de la base Railway
pg_dump "postgresql://usuario:password@host.railway.app:5432/railway" \
    --no-owner --no-acl -Fc > sonograma_railway_backup.fc

# Subir al servidor Lightsail
scp -i ~/.ssh/LightsailDefaultKey.pem sonograma_railway_backup.fc \
    ubuntu@<IP-ESTATICA>:/opt/sonograma/backups/
```

### En el servidor Lightsail:
```bash
# Restaurar formato custom de pg_dump
DB_USER=$(grep SPRING_DATASOURCE_USERNAME /etc/sonograma/sonograma.env | cut -d= -f2)
docker exec -i sonograma-postgres \
    pg_restore -U "$DB_USER" -d sonograma_db --no-owner --no-acl \
    < /opt/sonograma/backups/sonograma_railway_backup.fc
```

> Si el dump es formato plain SQL (`.sql` o `.sql.gz`), usar `restore-db.sh` directamente.

---

## 13. Desactivar Railway y Vercel

**Solo después de verificar que todo funciona en Lightsail:**

1. Verificar que `https://tudominio.com` carga el frontend correctamente
2. Verificar que `https://tudominio.com/api/actuator/health` responde `{"status":"UP"}`
3. Hacer login y probar las funciones principales
4. En Railway: ir al proyecto → Settings → Delete project (o suspender)
5. En Vercel: ir al proyecto → Settings → Delete project

---

## 14. Puertos del firewall Lightsail

En el panel de AWS Lightsail → tu instancia → **Networking** → Firewall:

| Puerto | Protocolo | Abrir | Descripción |
|---|---|---|---|
| 22 | TCP | ✅ Sí | SSH |
| 80 | TCP | ✅ Sí | HTTP |
| 443 | TCP | ✅ Sí | HTTPS |
| 5432 | TCP | ❌ No | PostgreSQL (solo interno Docker) |
| 8080 | TCP | ❌ No | Backend (solo accesible via Nginx) |

---

## 15. Checklist de migración

- [ ] Instancia Lightsail creada con IP estática
- [ ] Puertos 22/80/443 abiertos en firewall Lightsail
- [ ] `setup-server.sh` ejecutado sin errores
- [ ] `/etc/sonograma/sonograma.env` configurado con valores reales
- [ ] Dominio apunta a la IP estática
- [ ] `nginx-sonograma.conf` actualizado con dominio real
- [ ] Primer `deploy.sh` exitoso
- [ ] Datos migrados desde Railway
- [ ] Login funciona en el nuevo servidor
- [ ] Certificado SSL obtenido con Certbot
- [ ] Redireccionamiento HTTP→HTTPS activo

---

## 16. Checklist post-deploy

- [ ] `https://tudominio.com` carga sin errores
- [ ] `https://tudominio.com/api/actuator/health` → `{"status":"UP"}`
- [ ] Login funciona
- [ ] Se puede crear/editar un disco
- [ ] Se puede registrar una venta
- [ ] Backup automático configurado (`crontab -l`)
- [ ] Logs de backend sin errores (`docker logs sonograma-backend --tail 50`)
- [ ] Railway y Vercel desactivados
