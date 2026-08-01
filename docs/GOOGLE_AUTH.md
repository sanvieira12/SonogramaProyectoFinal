# Google Authentication — producción en AWS Lightsail

Esta integración agrega Google OpenID Connect sin reemplazar el login existente. No crea usuarios, tablas, migraciones, bases de datos, dominios ni infraestructura fuera de Lightsail.

## Cómo conviven ambos métodos

- El formulario actual mantiene `POST /api/auth/login` y devuelve el JWT existente.
- El botón Google inicia `GET /api/oauth2/authorization/google` en Spring Security.
- Google vuelve a `https://tiendasonograma.com/api/login/oauth2/code/google`.
- El backend exige `email`, `email_verified=true` y coincidencia exacta, normalizada, con `sonograma.tiendadediscos@gmail.com`.
- La identidad aprobada se mapea al usuario local existente `admin`; no se modifica su email, contraseña, rol ni fila.
- El backend genera el mismo tipo de JWT que el login por contraseña y lo guarda temporalmente detrás de un código aleatorio de un solo uso, válido durante 60 segundos.
- El navegador vuelve a `/login?oauth_code=...`, elimina inmediatamente el código de la URL y hace `POST /api/auth/google/exchange`. El JWT nunca aparece en una URL.
- Después del intercambio, ambas opciones usan `localStorage`, `Authorization: Bearer`, `/api/auth/session`, los mismos route guards y el mismo cierre de sesión.
- La sesión HTTP creada para conservar el `state` de OAuth se invalida al completar o fallar el flujo. No sustituye la autenticación JWT de las APIs.

Si `GOOGLE_CLIENT_ID` o `GOOGLE_CLIENT_SECRET` falta, Spring no registra el cliente OAuth, el backend sigue arrancando y el botón vuelve al login con un mensaje seguro. El login por contraseña queda disponible.

## URLs confirmadas por la implementación

| Uso | URL |
|---|---|
| Inicio de Google | `https://tiendasonograma.com/api/oauth2/authorization/google` |
| Callback autorizado | `https://tiendasonograma.com/api/login/oauth2/code/google` |
| Origen web, solo si Google lo solicita | `https://tiendasonograma.com` |

La callback no es una suposición: `application-prod.properties` fija `SONOGRAMA_GOOGLE_REDIRECT_URI` a esa URL, el registro de Spring usa ese valor y existe una prueba que lo verifica. Spring Security define `/oauth2/authorization/{registrationId}` como inicio y `/login/oauth2/code/{registrationId}` como callback; `/api` proviene del context path de Sonograma. Véase la [referencia oficial de Spring Security](https://docs.spring.io/spring-security/reference/servlet/oauth2/).

Nginx conserva `/api/*` y envía `Host`, `X-Forwarded-Proto`, `X-Forwarded-Host` y `X-Forwarded-Port`. Spring tiene `server.forward-headers-strategy=framework`; además, producción fija la callback canónica para que no cambie si alguien entra por otro hostname.

## Configuración en Google Cloud Console

1. Entrar a Google Cloud Console, crear o seleccionar un proyecto dedicado a Sonograma.
2. Abrir **Google Auth Platform** (en algunas vistas figura bajo **APIs & Services → OAuth consent screen**).
3. En **Branding**, configurar como mínimo:
   - App name: `Sonograma`.
   - User support email: una casilla controlada por Sonograma.
   - Developer contact email: una casilla controlada por Sonograma.
   - Homepage, si se solicita: `https://tiendasonograma.com`.
4. En **Audience**, seleccionar **External** si la cuenta permitida es una cuenta Gmail común y el proyecto no pertenece a una organización Google Workspace que permita `Internal`.
5. Mantener el publishing status en **Testing** durante esta fase y agregar como test user únicamente `sonograma.tiendadediscos@gmail.com`. Google limita las apps externas en testing a los usuarios de prueba configurados; el allowlist del backend sigue siendo obligatorio y es la defensa final. Véase [Manage App Audience](https://support.google.com/cloud/answer/15549945?hl=en).
6. En **Data Access**, solicitar únicamente `openid`, `email` y `profile`. El backend necesita `email` y el booleano `email_verified`; Google documenta ambos claims en su [referencia OIDC](https://developers.google.com/identity/openid-connect/reference).
7. Abrir **Clients**, elegir **Create Client** y seleccionar application type **Web application**.
8. Nombre sugerido: `Sonograma Producción`.
9. **Authorized JavaScript origins:** este flujo es completamente server-side y no necesita un origen JavaScript. Puede dejarse vacío. Si la consola exige uno por otra configuración del proyecto, agregar exactamente `https://tiendasonograma.com`, sin path ni slash final.
10. **Authorized redirect URIs:** agregar exactamente:

    ```text
    https://tiendasonograma.com/api/login/oauth2/code/google
    ```

    Es sensible a esquema, mayúsculas, path y slash final; Google requiere coincidencia exacta y devuelve `redirect_uri_mismatch` en caso contrario. Véase la [guía oficial para aplicaciones web](https://developers.google.com/identity/protocols/oauth2/web-server).
11. Crear el cliente y copiar el client ID y client secret una sola vez. No descargarlos dentro del repositorio ni pegarlos en archivos del frontend.

## Variables en Lightsail

Editar el archivo existente, que debe conservar permisos `600`:

```bash
sudoedit /etc/sonograma/sonograma.env
sudo chmod 600 /etc/sonograma/sonograma.env
```

Agregar:

```dotenv
GOOGLE_CLIENT_ID=valor_entregado_por_google
GOOGLE_CLIENT_SECRET=valor_entregado_por_google
SONOGRAMA_GOOGLE_ADMIN_EMAIL=sonograma.tiendadediscos@gmail.com
SONOGRAMA_GOOGLE_REDIRECT_URI=https://tiendasonograma.com/api/login/oauth2/code/google
```

Comprobar presencia sin imprimir secretos:

```bash
sudo awk -F= '
  /^(GOOGLE_CLIENT_ID|GOOGLE_CLIENT_SECRET|SONOGRAMA_GOOGLE_ADMIN_EMAIL|SONOGRAMA_GOOGLE_REDIRECT_URI)=/ {
    print $1, (length($2) ? "configurada" : "VACIA")
  }
' /etc/sonograma/sonograma.env
```

## Deploy seguro y acotado en Lightsail

No ejecutar `docker compose down`, `down -v`, `docker volume rm`, `DROP DATABASE` ni el script de restauración. Estos comandos asumen el checkout existente en `/opt/sonograma/app`.

### 1. Registrar versión, imágenes y volúmenes actuales

```bash
cd /opt/sonograma/app
DEPLOY_STAMP="$(date +%Y%m%d_%H%M%S)"
STATE_FILE="/opt/sonograma/backups/pre_google_oauth_${DEPLOY_STAMP}.txt"
{
  git rev-parse HEAD
  docker compose --env-file /etc/sonograma/sonograma.env -f docker-compose.prod.yml ps
  docker compose --env-file /etc/sonograma/sonograma.env -f docker-compose.prod.yml images
  docker volume ls --filter name=sonograma
} > "$STATE_FILE"
test -s "$STATE_FILE"
```

En el repositorio de origen, etiquetar y publicar el commit estable antes del merge/deploy:

```bash
git tag -a pre-google-oauth-20260801 -m "Sonograma estable antes de Google OAuth" <COMMIT_ESTABLE>
git push origin pre-google-oauth-20260801
```

### 2. Backup obligatorio, no vacío y válido

```bash
cd /opt/sonograma/app
./deploy/backup-db.sh
LATEST_BACKUP="$(ls -1t /opt/sonograma/backups/sonograma_db_*.sql.gz | head -1)"
test -s "$LATEST_BACKUP"
gzip -t "$LATEST_BACKUP"
ls -lh "$LATEST_BACKUP"
```

`backup-db.sh` ahora cancela si el archivo queda vacío o el gzip es inválido. `deploy.sh` también se detiene si falla el backup.

### 3. Actualizar y compilar

```bash
cd /opt/sonograma/app
git fetch origin
git switch main
git pull --ff-only origin main

cd /opt/sonograma/app/frontend
npm ci
VITE_API_URL=/api npm run build

cd /opt/sonograma/app
docker compose --env-file /etc/sonograma/sonograma.env -f docker-compose.prod.yml build backend
```

### 4. Recrear solo backend y recargar Nginx

```bash
cd /opt/sonograma/app
docker compose --env-file /etc/sonograma/sonograma.env -f docker-compose.prod.yml up -d --no-deps backend
docker exec sonograma-nginx nginx -t
docker exec sonograma-nginx nginx -s reload
```

PostgreSQL no se recrea ni reinicia. Los volúmenes `postgres_data`, logs, portadas y media permanecen montados.

### 5. Salud y logs sin secretos

```bash
docker compose --env-file /etc/sonograma/sonograma.env -f /opt/sonograma/app/docker-compose.prod.yml ps
docker exec sonograma-backend curl -fsS http://localhost:8080/api/actuator/health
curl -fsS https://tiendasonograma.com/api/actuator/health
docker logs sonograma-backend --since 10m
docker logs sonograma-nginx --since 10m
```

No copiar logs a tickets públicos. Confirmar que no aparecen client secret, JWT, token de Google ni códigos OAuth reutilizables.

## Verificación de producción

1. Abrir `https://tiendasonograma.com`; confirmar certificado HTTPS válido, sin mixed content, CORS ni redirect loop.
2. Ingresar con el usuario y contraseña existentes; abrir dashboard, refrescar, abrir segunda pestaña y cerrar sesión.
3. Pulsar **Ingresar con Google**, elegir `sonograma.tiendadediscos@gmail.com`, confirmar dashboard, refrescar y abrir segunda pestaña.
4. Cerrar sesión y confirmar que vuelve a `/login` y ya no accede a una ruta protegida.
5. Repetir en Safari de iPhone y Chrome o Safari de macOS. Las cookies temporales OAuth usan `Secure`, `HttpOnly` y `SameSite=Lax`.
6. Intentar con otra cuenta Google. Debe volver al login y mostrar que la cuenta no está autorizada; el intercambio responde `403` y no crea ningún usuario.
7. Cancelar en Google. Debe volver al login con un mensaje de cancelación sin detalles internos.
8. Confirmar que la URL del navegador nunca contiene un JWT, access token, ID token, client secret o credenciales.
9. Revisar catálogo, stock, clientes, ventas, deudas, pedidos, pre-ventas, importaciones, QR, Discogs, VinylFuture y reportes, confirmando que muestran los datos existentes.

Si aparece `redirect_uri_mismatch`, comparar carácter por carácter el valor de Google Cloud con `https://tiendasonograma.com/api/login/oauth2/code/google`. Si el botón informa configuración ausente, revisar que ambos valores `GOOGLE_CLIENT_ID` y `GOOGLE_CLIENT_SECRET` estén presentes y recrear únicamente el backend.

## Rollback sin tocar PostgreSQL

El login por contraseña no depende del cliente Google y debe seguir operativo aunque falle Google. Para desactivar Google de inmediato sin rollback de código:

```bash
sudoedit /etc/sonograma/sonograma.env
# Dejar GOOGLE_CLIENT_ID= y GOOGLE_CLIENT_SECRET= vacíos
cd /opt/sonograma/app
docker compose --env-file /etc/sonograma/sonograma.env -f docker-compose.prod.yml up -d --no-deps backend
```

Para volver al código estable registrado:

```bash
cd /opt/sonograma/app
git fetch --tags origin
git switch --detach pre-google-oauth-20260801

cd frontend
npm ci
VITE_API_URL=/api npm run build

cd ..
docker compose --env-file /etc/sonograma/sonograma.env -f docker-compose.prod.yml build backend
docker compose --env-file /etc/sonograma/sonograma.env -f docker-compose.prod.yml up -d --no-deps backend
docker exec sonograma-nginx nginx -t
docker exec sonograma-nginx nginx -s reload
docker exec sonograma-backend curl -fsS http://localhost:8080/api/actuator/health
```

No restaurar PostgreSQL salvo evidencia real e independiente de daño de datos. Este cambio no incluye migraciones ni escrituras de esquema, por lo que un rollback normal conserva `postgres_data`, media, imágenes, logs y todos los registros.
