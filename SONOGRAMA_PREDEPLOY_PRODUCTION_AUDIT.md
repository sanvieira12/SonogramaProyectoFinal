# Sonograma Pre-Deploy Production Read-Only Audit

## 1. Environment checked

Audit performed from the local workspace:

```text
/Users/admin/Developer/sonograma
HEAD: f50e6ab (agent/fix-catalog-permanent-deletion, origin/agent/fix-catalog-permanent-deletion)
Host: macOS, America/Montevideo local display, 2026-09-14
```

The repository deployment configuration, production properties, Docker Compose file, Dockerfiles, deployment scripts, backup script, and financial migrations were inspected read-only.

Production access was not available from this workspace:

- `/etc/sonograma/sonograma.env` was unavailable.
- `/opt/sonograma/app` production checkout was unavailable.
- Docker was installed, but the local Docker daemon was not running.
- No AWS CLI was available.
- No production PostgreSQL connection was attempted with invented or local credentials.

Therefore, production schema, data, PostgreSQL runtime, and container runtime results are **not claimed**. Exact commands to run on Lightsail are included below.

## 2. Schema compatibility

The application model and repository migrations require the following columns:

| Table | Required by Phases 1–4 | Repository evidence |
|---|---|---|
| `pago_deuda` | `anulado`, `fecha_anulacion`, `anulado_por` | `docs/migraciones/031_pago_deuda_anulacion.sql` and `PagoDeuda` |
| `pago_deuda` | `idempotency_key` | `docs/migraciones/030_pago_deuda_recibo_idempotencia.sql` and `PagoDeuda` |
| `deuda` | `monto_pagado_inicial` | `docs/migraciones/029_deudas_balance_por_movimiento.sql` and `Deuda` |
| `deuda` | `monto_pagado`, `monto_pendiente`, `id_venta` | `Deuda` and migration 029/base schema |
| `deuda` | `activa` | `docs/migraciones/013_deudas_activas_tipo_cambio_50.sql` and `Deuda` |

The prompt refers to `deuda.activo`; the current application schema uses `deuda.activa`. This naming difference must be checked explicitly in production. A database with `activo` but not `activa` is incompatible with the current code. A database with `activa` is compatible with the application’s active-debt model.

Production verification command — SELECT-only, to run on Lightsail after loading the production environment:

```bash
set -a
source /etc/sonograma/sonograma.env
set +a

docker exec sonograma-postgres psql -X -v ON_ERROR_STOP=1 \
  -U "${SPRING_DATASOURCE_USERNAME:-sonograma_user}" \
  -d sonograma_db \
  -c "
    SELECT table_name, column_name, data_type, is_nullable, column_default
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND (
        (table_name = 'pago_deuda' AND column_name IN ('anulado', 'fecha_anulacion', 'anulado_por', 'idempotency_key'))
        OR
        (table_name = 'deuda' AND column_name IN ('monto_pagado_inicial', 'monto_pagado', 'monto_pendiente', 'id_venta', 'activa', 'activo'))
      )
    ORDER BY table_name, column_name;
  "
```

Expected blocking result: all four `pago_deuda` columns, all five canonical `deuda` columns, and `deuda.activa` must be present. Missing required columns must block deployment.

Production schema status: **UNVERIFIED — production access unavailable**.

## 3. Historical-data findings

No production rows were read or modified. No `UPDATE`, `DELETE`, `INSERT`, `ALTER`, `DROP`, `TRUNCATE`, migration, repair, deployment, Docker restart, or rebuild was executed.

Run the following SELECT-only diagnostics on Lightsail to return invalid initial amounts, balance mismatches, duplicate idempotency keys, orphan payments, and suspicious dates:

```sql
-- A. Invalid initial payment
SELECT id_deuda, id_venta, monto_total, monto_pagado_inicial
FROM deuda
WHERE monto_pagado_inicial IS NULL
   OR monto_pagado_inicial < 0
   OR monto_pagado_inicial > monto_total;

-- B. Debt cached balance mismatch, using only non-annulled payments
SELECT d.id_deuda,
       d.monto_total,
       d.monto_pagado_inicial,
       d.monto_pagado,
       d.monto_pendiente,
       expected.expected_paid,
       GREATEST(d.monto_total - expected.expected_paid, 0) AS expected_pending
FROM deuda d
JOIN (
    SELECT d2.id_deuda,
           LEAST(
               d2.monto_total,
               COALESCE(d2.monto_pagado_inicial, 0)
               + COALESCE(SUM(CASE WHEN p.anulado IS DISTINCT FROM TRUE THEN COALESCE(p.monto, 0) ELSE 0 END), 0)
           ) AS expected_paid
    FROM deuda d2
    LEFT JOIN pago_deuda p ON p.id_deuda = d2.id_deuda
    GROUP BY d2.id_deuda, d2.monto_total, d2.monto_pagado_inicial
) expected ON expected.id_deuda = d.id_deuda
WHERE d.monto_pagado IS DISTINCT FROM expected.expected_paid
   OR d.monto_pendiente IS DISTINCT FROM GREATEST(d.monto_total - expected.expected_paid, 0);

-- C. Venta / Deuda cache mismatch
SELECT d.id_deuda, d.id_venta,
       d.monto_pagado AS deuda_monto_pagado,
       v.monto_pagado AS venta_monto_pagado,
       d.monto_pendiente AS deuda_monto_pendiente,
       v.monto_deuda AS venta_monto_deuda,
       d.estado_pago AS deuda_estado_pago,
       v.estado_pago AS venta_estado_pago
FROM deuda d
JOIN venta v ON v.id_venta = d.id_venta
WHERE d.monto_pagado IS DISTINCT FROM v.monto_pagado
   OR d.monto_pendiente IS DISTINCT FROM v.monto_deuda
   OR d.estado_pago IS DISTINCT FROM v.estado_pago;

-- D. All annulled payments and current debt cache
SELECT p.id_pago_deuda,
       p.id_deuda,
       p.monto,
       p.fecha_pago,
       p.fecha_anulacion,
       p.anulado_por,
       d.monto_pagado,
       d.monto_pendiente
FROM pago_deuda p
LEFT JOIN deuda d ON d.id_deuda = p.id_deuda
WHERE p.anulado = TRUE
ORDER BY p.fecha_anulacion, p.id_pago_deuda;

-- E. Duplicate non-null idempotency keys in one debt
SELECT id_deuda, idempotency_key, COUNT(*) AS cantidad
FROM pago_deuda
WHERE idempotency_key IS NOT NULL
GROUP BY id_deuda, idempotency_key
HAVING COUNT(*) > 1;

-- F. Orphan payments
SELECT p.id_pago_deuda, p.id_deuda
FROM pago_deuda p
LEFT JOIN deuda d ON d.id_deuda = p.id_deuda
WHERE d.id_deuda IS NULL;

-- G. Suspicious financial dates; adjust the agreed operating range if needed
SELECT 'venta' AS origen, id_venta AS id, fecha_venta AS fecha
FROM venta
WHERE fecha_venta < TIMESTAMP '2020-01-01'
   OR fecha_venta > TIMESTAMP '2035-12-31'
UNION ALL
SELECT 'pago_deuda', id_pago_deuda, fecha_pago::timestamp
FROM pago_deuda
WHERE fecha_pago < DATE '2020-01-01'
   OR fecha_pago > DATE '2035-12-31'
UNION ALL
SELECT 'deuda', id_deuda, fecha_deuda::timestamp
FROM deuda
WHERE fecha_deuda < DATE '2020-01-01'
   OR fecha_deuda > DATE '2035-12-31';
```

Historical-data status: **UNVERIFIED — no production result was available**.

## 4. Venta/Deuda reconciliation findings

The current code recalculates linked debt state from:

```text
LEAST(monto_total, monto_pagado_inicial + SUM(non-annulled PagoDeuda.monto))
```

and updates `Venta.montoPagado`, `Venta.montoDeuda`, and `Venta.estadoPago` together. The final local regression audit verified this behavior, including payment reversal, inactive debt, and cancelled-sale historical payment cases.

Production cache reconciliation is still unverified. Run query B and C from section 3 before deployment. Any returned row should be understood and documented; no repair is authorized by this audit.

## 5. Payment/reversal findings

Local code audit result:

- Payment reversal retains the `PagoDeuda` row.
- Reversal sets `anulado`, `fechaAnulacion`, and `anuladoPor`.
- Original payment amount/date/creation/idempotency identity is preserved.
- Annulled rows are excluded through `FinancialMovementPolicy`.
- Debt and linked sale caches are recalculated.
- A second reversal returns a clear business validation without a second subtraction.

Production annulled-payment contents are unverified. Query D above is SELECT-only and must be run manually.

## 6. Timezone/runtime findings

Local-only observations:

- Local host clock reported `2026-09-14 14:12:33 -03 -0300`.
- Local workspace is macOS, not the Lightsail Linux/container runtime.
- Local JVM is Java 21.0.11; its default timezone was not explicitly set in the inspected settings output.
- Docker daemon was unavailable, so container clocks were not inspected.
- PostgreSQL production timezone/current timestamp were not inspected.

Production read-only runtime commands:

```bash
# Linux host
date -Is
date '+%Y-%m-%d %H:%M:%S %Z %z'
readlink -f /etc/localtime 2>/dev/null || true
cat /etc/timezone 2>/dev/null || true

# Containers
docker exec sonograma-backend date -Is
docker exec sonograma-postgres date -Is

# JVM default timezone inside the running backend container
docker exec sonograma-backend sh -c \
  'java -XshowSettings:properties -version 2>&1 | grep -E "user.timezone|user.country|java.version"'
```

PostgreSQL read-only check:

```bash
docker exec sonograma-postgres psql -X -v ON_ERROR_STOP=1 \
  -U "${SPRING_DATASOURCE_USERNAME:-sonograma_user}" \
  -d sonograma_db \
  -c "
    SELECT current_setting('TimeZone') AS postgres_timezone,
           current_date AS postgres_date,
           current_timestamp AS postgres_timestamp,
           now() AT TIME ZONE 'America/Montevideo' AS montevideo_local;
  "
```

The application code itself uses `BusinessTime` with `America/Montevideo` and injectable clocks for financial business dates. Production runtime timezone status remains **UNVERIFIED**.

## 7. Migration compatibility

The relevant migration files are present locally:

- `docs/migraciones/029_deudas_balance_por_movimiento.sql`
- `docs/migraciones/030_pago_deuda_recibo_idempotencia.sql`
- `docs/migraciones/031_pago_deuda_anulacion.sql`
- `docs/migraciones/013_deudas_activas_tipo_cambio_50.sql`
- `docs/migraciones/025_venta_numero_recibo.sql`

Migration 031 adds the reversal columns but also contains a data-writing statement:

```sql
UPDATE pago_deuda SET anulado = FALSE WHERE anulado IS NULL;
```

Migration 029 contains multiple `UPDATE` statements that backfill and normalize debt balances. They were not executed.

The production schema cannot be compared to these migrations without production access. The manual schema query in section 2 must be run first. No migration should be executed during this audit.

## 8. Deployment-script review

The inspected deployment path is AWS Lightsail with Docker Compose:

```text
Nginx -> backend container -> PostgreSQL container
```

Findings:

- AWS Lightsail is the documented deployment target.
- `docker-compose.prod.yml` uses a named `postgres_data` volume mounted at `/var/lib/postgresql/data`; the inspected deploy script does not run `docker compose down -v`, so the normal deploy path does not remove that volume.
- Frontend build path is `frontend`, output is `frontend/dist`, and Nginx mounts that directory read-only.
- Backend build context is `sonograma-backend`; the Dockerfile builds the current Maven project and packages the current source tree.
- No Vercel or Railway deployment path is configured in `docker-compose.prod.yml` or `deploy/deploy.sh`. References to Railway/Vercel are historical documentation only.
- Production properties use `spring.jpa.hibernate.ddl-auto=validate`, which is appropriate for refusing an incomplete schema at application startup.
- The Docker backend image build uses `mvn clean package -DskipTests`; deployment does not run backend tests in the image build.
- `deploy/deploy.sh` automatically loops through `docs/migraciones/*.sql` and executes them with `psql`.
- Migration failures are converted to warnings and deployment continues:

```bash
... psql ... < "$MIGRATION" || warn "Migración ... falló o ya estaba aplicada, continuando..."
```

This fail-open behavior is a material deployment safety risk. A partially applied or incompatible migration could be followed by a build/restart, and the script may continue after a migration error. The script was not executed or modified.

Deployment-script result: **not safe to approve without an explicit migration/schema gate or manual confirmation that all required migrations are already applied**.

## 9. Backup command

The repository’s intended pre-deployment backup entry point is:

```bash
cd /opt/sonograma/app
/opt/sonograma/app/deploy/backup-db.sh
```

That script reads `/etc/sonograma/sonograma.env`, uses `pg_dump` against `sonograma-postgres` when the container is present, writes a gzip backup under `/opt/sonograma/backups`, validates it with `gzip -t`, and retains recent backups. It was **not executed** because it writes a backup and rotates old files, and this task is strictly read-only.

If a direct one-shot command is preferred immediately before deployment, after loading the production environment:

```bash
set -a
source /etc/sonograma/sonograma.env
set +a
mkdir -p /opt/sonograma/backups
docker exec sonograma-postgres pg_dump \
  -U "${SPRING_DATASOURCE_USERNAME:-sonograma_user}" \
  -d sonograma_db --no-password \
  | gzip > "/opt/sonograma/backups/sonograma_db_$(date '+%Y%m%d_%H%M%S').sql.gz"
```

The command above is provided for manual execution only and was not run.

## 10. Blocking issues

1. Production schema compatibility is unverified because Lightsail/PostgreSQL access was unavailable. Missing reversal or balance columns would block deployment.
2. The production `deuda` active-column naming is unverified. The current code requires `activa`, while the audit request names `activo`.
3. `deploy/deploy.sh` executes migrations automatically and continues after migration failure. This fail-open behavior is not safe for approval without a separate preflight/manual migration decision.
4. Production historical-data diagnostics were not run, so cache mismatches, orphan rows, duplicate idempotency keys, and suspicious dates are unknown.

## 11. Non-blocking risks

- The application’s financial business-time logic is centralized and locally verified, but production host/container/PostgreSQL timezone values remain undocumented until the runtime commands are executed.
- The backend Docker build skips tests (`-DskipTests`); tests must be run before the deployment command as a separate release gate.
- The financial income calculator has a potential N+1 lookup pattern for sale-linked debts, documented by the final local audit.
- The frontend financial-change event is current-browser-tab local; it is not cross-tab or cross-device synchronization.
- The backup script’s retention step deletes backups older than its retention window; this was not executed during the read-only audit and should be reviewed as part of operational backup policy.

## 12. Final deployment recommendation

**NOT READY FOR DEPLOYMENT**

The recommendation is based on the unavailable production schema/data verification and the fail-open migration behavior in `deploy/deploy.sh`. The local code and regression suites are compatible with the four financial phases, but controlled deployment should wait until the production SELECT-only checks pass, the backup is manually completed and validated, and the migration/schema gate is explicitly resolved.
