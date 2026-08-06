# Registro final de despliegue y entrega académica

- **Nombre de la aplicación:** Sonograma.
- **URL de producción:** https://tiendasonograma.com
- **Repositorio público:** https://github.com/sanvieira12/SonogramaProyectoFinal
- **Rama final:** `agent/fix-catalog-permanent-deletion`.
- **Commit final:** commit al que apunta `v1.0-entrega-ort^{commit}`; el SHA exacto se publica en el informe final de la entrega.
- **Etiqueta académica:** `v1.0-entrega-ort`.
- **Fecha y hora del despliegue:** 3 de agosto de 2026, 12:27:54 UYT (15:27:54 UTC), según el inicio del contenedor backend verificado.
- **Resumen de infraestructura:** instancia AWS Lightsail con IP estática; Nginx como terminación HTTPS, servidor del frontend y proxy de `/api`; Spring Boot para la API; PostgreSQL 16 con volumen persistente; certificados Let's Encrypt y respaldos automáticos diarios.
- **Servicios de Docker Compose:** `postgres` (`sonograma-postgres`), `backend` (`sonograma-backend`) y `nginx` (`sonograma-nginx`). Los tres estaban activos; PostgreSQL y backend reportaban estado saludable.
- **Estado de migraciones:** esquema de producción compatible con `ddl-auto=validate`; migración `037_catalog_permanent_deletion.sql` verificada mediante las columnas `catalog_deleted_at`, `catalog_deleted_by` y el índice parcial `idx_disco_catalog_active`.
- **Pruebas automatizadas:** frontend, 77 pruebas aprobadas en 14 archivos; backend, 201 pruebas ejecutadas sin fallos ni errores y 1 omitida. ESLint terminó sin errores.
- **Compilación del frontend:** exitosa con Vite 8.0.13; 859 módulos transformados.
- **Fecha del respaldo:** 5 de agosto de 2026, 00:00:01 UYT (03:00:01 UTC); archivo automático `sonograma_db_20260805_030001.sql.gz`.
- **Verificación del respaldo:** archivo no vacío (152.615 bytes) y validación `gzip -t` exitosa. No se ejecutó una restauración completa para evitar modificar producción.
- **Pruebas de humo:** redirección HTTP→HTTPS `301`; frontend principal y `www` con `200`; healthcheck público e interno con `{"status":"UP"}`; endpoint protegido sin autenticación con `403`; inicio de Google OAuth con `302` hacia Google y callback HTTPS canónica.
- **Commit de rollback:** `8f2232f52fab1a25ea442fb85e8744350b21a933`, padre directo del cierre académico y con el mismo árbol funcional desplegado que `2f934ad405b5c50850a6345117a44f149d68014e`.
- **Limitaciones conocidas:** las migraciones se administran como scripts SQL y no mediante una tabla automática de versionado; la verificación del respaldo cubre integridad, no una restauración aislada completa; Google OAuth depende de la disponibilidad del proveedor y de la cuenta autorizada configurada.
- **Estado del acceso para correctores:** Pendiente de definición por ORT; todavía no se creó una cuenta para correctores.

Este registro no contiene contraseñas, tokens, claves privadas ni otros secretos de producción.
