# Sonograma — Production Preflight Result

## 1. Production Access

**EXISTING LIGHTSAIL ACCESS RECOVERED.** The documented SSH method worked using the existing local key:

```bash
ssh -i ~/.ssh/LightsailDefaultKey-us-east-1.pem \
  -o BatchMode=yes -o ConnectTimeout=10 -o StrictHostKeyChecking=yes \
  ubuntu@tiendasonograma.com 'hostname && whoami && pwd'
```

Read-only result: `ip-172-26-10-67`, user `ubuntu`, working directory `/home/ubuntu`.

No production write, deployment, migration, restart, or backup creation was performed.

## 2. Production Environment

| Item | Verified result |
|---|---|
| Application path | `/opt/sonograma/app` |
| Environment file | `/etc/sonograma/sonograma.env` exists; values were not printed |
| Git commit | `f50e6ab3dc7d4b6452d94e5cd72cf427c45d4fd5` |
| Git branch | `agent/fix-catalog-permanent-deletion`, tracking origin |
| PostgreSQL | `sonograma-postgres`, `postgres:16-alpine`, healthy, up 8 weeks |
| Backend | `sonograma-backend`, healthy, up 2 days |
| Nginx | `sonograma-nginx`, up 2 days |
| Persistent production volume | `postgres_data` is configured by the production Compose file |

Separate `sonograma-vinylfuture-test-*` containers were present and were not treated as the production service set.

## 3. Schema Verification

Production schema query executed with `information_schema` using `docker exec sonograma-postgres psql`. Result: **9 relevant rows returned**.

The required production schema is present. The legacy/incompatible `deuda.activo` column was **not returned**.

## 4. Required Financial Columns

Exact production columns and types:

| Table | Column | Type | Nullable | Default |
|---|---|---|---|---|
| `deuda` | `activa` | `boolean` | NO | `true` |
| `deuda` | `id_venta` | `bigint` | YES | — |
| `deuda` | `monto_pagado` | `numeric` | NO | — |
| `deuda` | `monto_pagado_inicial` | `numeric` | NO | `0` |
| `deuda` | `monto_pendiente` | `numeric` | NO | — |
| `pago_deuda` | `anulado` | `boolean` | NO | `false` |
| `pago_deuda` | `anulado_por` | `character varying` | YES | — |
| `pago_deuda` | `fecha_anulacion` | `timestamp without time zone` | YES | — |
| `pago_deuda` | `idempotency_key` | `character varying` | YES | — |

Required-column result: **satisfied**. `deuda.activa` exists; `deuda.activo` does not appear in the production result.

Relevant indexes also exist:

- `uq_pago_deuda_idempotency`: unique on `(id_deuda, idempotency_key)` for non-null keys.
- Active-debt indexes using `deuda.activa` exist.

## 5. Historical Data Diagnostics

All seven requested diagnostics were executed as `SELECT` statements. Counts:

| Diagnostic | Description | Rows |
|---|---|---:|
| A | Invalid initial payment | 0 |
| B | Debt cached balance mismatch | 0 |
| C | Venta / Deuda cache mismatch | 1 |
| D | Annulled payments | 0 |
| E | Duplicate idempotency-key groups | 0 |
| F | Orphan payments | 0 |
| G | Suspicious financial dates | 0 |

No rows were changed. A, B, D, E, F, and G returned no result rows.

## 6. Debt Balance Reconciliation

Diagnostic B returned **0 mismatches** across the production debt set. The production counts used for the check were:

- `deuda` rows: 96
- `pago_deuda` rows: 31
- `pago_deuda.anulado IS NULL`: 0
- Invalid initial-payment rows: 0

Using the required formula — initial payment plus non-annulled payments, capped at `monto_total` — the cached `monto_pagado` and `monto_pendiente` values matched for all checked debts.

No repair was performed.

## 7. Venta / Deuda Reconciliation

Diagnostic C returned **1 mismatch**:

| `id_deuda` | `id_venta` | Debt paid | Sale paid | Debt pending | Sale debt | Debt status | Sale status |
|---:|---:|---:|---:|---:|---:|---|---|
| 30 | 25 | 1500.00 | 1500.00 | 800.00 | 400.00 | PARCIAL | PARCIAL |

Read-only context for the mismatch:

- Debt 30: total `2300.00`, initial paid `1500.00`, paid `1500.00`, pending `800.00`, inactive, dated `2026-07-06`.
- Sale 25: price/subtotal/total/final `1900.00`, paid `1500.00`, debt `400.00`, status `COMPLETADA`, payment status `PARCIAL`.
- No `pago_deuda` rows are attached to debt 30.

This is an unresolved financial cache inconsistency. It was not repaired.

## 8. Annulled Payments

Annulled payment query D returned **0 rows**.

The production `pago_deuda` table contains 31 rows, all with a non-null `anulado` value. No annulled payment review item was returned, and no payment was modified.

## 9. Idempotency and Orphan Checks

| Check | Production result |
|---|---:|
| Duplicate non-null idempotency-key groups | 0 |
| Orphan payments | 0 |

The production unique partial idempotency index is present.

## 10. Financial Date Check

The requested range check (`2020-01-01` through `2035-12-31`) returned **0 suspicious rows** across:

- `venta.fecha_venta`
- `deuda.fecha_deuda`
- `pago_deuda.fecha_pago`

No dates were changed.

## 11. Timezone and Runtime

Read-only runtime results:

| Runtime | Result |
|---|---|
| Lightsail host | `2026-09-14 17:44:49 UTC +0000` |
| Backend container | `2026-09-14T17:44:50+00:00` |
| PostgreSQL container | `2026-09-14T17:44:50+00:00` |
| JVM | Java `21.0.12`; country `US`; `user.timezone` was not emitted by the settings output |
| PostgreSQL timezone | `UTC` |
| PostgreSQL current date | `2026-09-14` |
| Montevideo conversion | `2026-09-14 14:44:50` |

UTC runtime is compatible with the application’s explicit `America/Montevideo` `BusinessTime` handling. No runtime restart was performed.

## 12. Migration State

The resulting production schema/state was inspected without executing migration files:

| Migration | Resulting production requirement | Status |
|---|---|---|
| `013_deudas_activas_tipo_cambio_50.sql` | `deuda.activa` boolean exists | Satisfied |
| `029_deudas_balance_por_movimiento.sql` | `monto_pagado_inicial` and balance columns exist; balance diagnostic clean | Satisfied by observed state |
| `030_pago_deuda_recibo_idempotencia.sql` | `idempotency_key` and unique partial index exist | Satisfied |
| `031_pago_deuda_anulacion.sql` | annulment columns exist; default false; no null `anulado` values | Satisfied |

The files’ existence was not used as proof that they ran. No migration was executed. Migration files 029 and 031 contain data-writing `UPDATE` statements and remain subject to controlled migration handling.

## 13. Deploy Script Risk

`deploy/deploy.sh` was inspected locally and on the production checkout, but not executed or modified.

It still:

1. Automatically loops through `docs/migraciones/*.sql`.
2. Runs each file with `psql`.
3. Converts migration failure into a warning and continues:

```bash
psql ... < "$MIGRATION" || warn "Migración ... falló o ya estaba aplicada, continuando..."
```

This fail-open behavior remains a material deployment risk. A future incompatible or partially applied migration could be followed by build and service replacement.

## 14. Backup Readiness

`/opt/sonograma/app/deploy/backup-db.sh` exists and was inspected; it was not executed.

- Expected directory: `/opt/sonograma/backups`.
- Method: Docker `pg_dump` of `sonograma_db`, piped through `gzip`.
- Validation: `gzip -t` validates the generated archive.
- Retention: `find` removes archives older than the configured retention window.
- Existing latest archive: `sonograma_db_20260914_030002.sql.gz`.
- Existing latest archive validation: `gzip -t` passed; no archive was changed or deleted.

Exact manual command to run immediately before an authorized deployment:

```bash
cd /opt/sonograma/app
/opt/sonograma/app/deploy/backup-db.sh
```

## 15. Blocking Issues

1. One unresolved Venta/Deuda financial inconsistency exists: debt 30 reports pending `800.00`, while linked sale 25 reports debt `400.00`.
2. `deploy.sh` retains fail-open migration handling and can continue after a migration error.

## 16. Non-Blocking Risks

- Production runs in UTC while application financial business time is explicitly `America/Montevideo`; this is currently compatible, but should remain documented.
- The backend Docker build uses `mvn clean package -DskipTests`; local tests must remain a release gate.
- Separate Vinyl Future test containers are running on the host and should be reviewed operationally, although they are not part of the named production Compose services.
- The financial income calculator has a potential N+1 lookup pattern for sale-linked debts.
- The frontend financial-change event is limited to the current browser tab.
- Backup retention behavior should be reviewed against the operational backup policy.

## 17. Final Recommendation

**NOT READY FOR DEPLOYMENT**
