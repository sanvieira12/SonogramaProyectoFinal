# Sonograma Production Rollout — 2026-09-14

## 1. Local Release Gate

The local release gate passed.

| Check | Result |
|---|---|
| `git rev-parse HEAD` | `f50e6ab3dc7d4b6452d94e5cd72cf427c45d4fd5` |
| Branch | `agent/fix-catalog-permanent-deletion` |
| Worktree | Dirty with the previously completed Sonograma changes and reports; preserved unchanged |
| Backend | `mvn -q test` passed |
| Frontend tests | 20 files passed; 138 tests passed |
| Frontend lint | Passed |
| Frontend build | Passed with Vite |
| Shell syntax | `deploy.sh`, `backup-db.sh`, `baseline-migrations.sh`, and `test-migration-tracking.sh` passed `bash -n` |
| `git diff --check` | Passed |
| Migration tracking mock test | Passed all baseline/repeat/skip/new/failure/checksum/ledger scenarios |

## 2. Production Read-Only Preflight

The recovered SSH connection succeeded.

| Check | Result |
|---|---|
| Hostname | `ip-172-26-10-67` |
| User | `ubuntu` |
| Working directory | `/home/ubuntu` |
| Application path | `/opt/sonograma/app` |
| Production Git commit | `f50e6ab3dc7d4b6452d94e5cd72cf427c45d4fd5` |
| Production branch | `agent/fix-catalog-permanent-deletion` |
| Public site | HTTP 200 |
| Public API health | `{"status":"UP"}` |

Production preflight showed PostgreSQL, backend, and Nginx running. The backend and PostgreSQL containers were healthy; Nginx was running. The production checkout status was clean in the captured output.

## 3. Fresh Backup

The mandatory fresh backup completed before the rollout stopped.

| Field | Result |
|---|---|
| Backup path | `/opt/sonograma/backups/sonograma_db_20260914_203851.sql.gz` |
| Backup timestamp | `2026-09-14 20:38:52.703412017 +0000` |
| Size | 799,851 bytes |
| Non-empty check | Passed |
| `gzip -t` | Passed |

The backup is available. No backup was deleted or modified.

## 4. Migration Baseline

Read-only inspection confirmed that `sonograma_schema_migrations` did not exist in production.

The required command was then attempted exactly as specified:

```bash
cd /opt/sonograma/app
./deploy/baseline-migrations.sh --confirm-production-baseline
```

It failed immediately because the script is absent from the production checkout:

```text
bash: line 1: ./deploy/baseline-migrations.sh: No such file or directory
```

No baseline was created. No historical migration 001–047 was executed. No migration ledger row was inserted. No workaround or manual SQL was attempted.

## 5. Deployment

Not started.

`deploy/deploy.sh` was not executed. No Git update, migration execution, frontend deployment, backend image build, service replacement, or application deployment occurred.

## 6. Infrastructure Health

Last successful read-only infrastructure checkpoint, before the baseline failure:

| Service | Last known status |
|---|---|
| `sonograma-postgres` | Up 8 weeks, healthy |
| `sonograma-backend` | Up 3 days, healthy |
| `sonograma-nginx` | Up 3 days |
| Public site | HTTP 200 |
| `/api/actuator/health` | `UP` |

No infrastructure health command was run after the stop because the fail-closed rule prohibits continuing past the failed baseline checkpoint.

## 7. Financial Database Smoke Test

The pre-deployment read-only checks passed before the baseline attempt:

- Required financial schema columns were present, including `deuda.activa`, `deuda.monto_pagado_inicial`, `pago_deuda.anulado`, `pago_deuda.fecha_anulacion`, `pago_deuda.anulado_por`, and `pago_deuda.idempotency_key`.
- Null `pago_deuda.anulado` rows: 0.
- Orphan payments: 0.
- Duplicate non-null idempotency groups: 0.

The five protected pairs also matched their audited values immediately before the baseline attempt:

| Debt / sale | Debt total | Sale total | Debt pending | Sale debt | Debt active | Sale state |
|---:|---:|---:|---:|---:|---|---|
| 30 / 25 | 2,300.00 | 1,900.00 | 800.00 | 400.00 | false | COMPLETADA |
| 53 / 34 | 790.00 | 1,390.00 | 790.00 | 790.00 | true | COMPLETADA |
| 67 / 48 | 8,640.00 | 17,573.00 | 8,640.00 | 8,640.00 | true | COMPLETADA |
| 89 / 76 | 1,000.00 | 1,350.00 | 1,000.00 | 1,000.00 | false | CANCELADA |
| 102 / 90 | 1,690.00 | 1,290.00 | 1,690.00 | 1,690.00 | true | COMPLETADA |

The post-deployment financial smoke test was not run because deployment did not begin.

## 8. Application Smoke Test

Not run. No application-level smoke test was performed after the failed baseline checkpoint. The public root and API health endpoint had passed during the pre-deployment read-only check.

No real customer or financial data was created or changed for testing.

## 9. Cross-Report Verification

Not run. The rollout stopped before deployment and before the post-deployment current-month comparison of Libro de Ventas, Dashboard, and monthly financial summary.

## 10. Historical Record Preservation

The five audited historical Venta/Deuda pairs were verified immediately before the baseline attempt and were unchanged in that check:

- Debt 30 / Sale 25
- Debt 53 / Sale 34
- Debt 67 / Sale 48
- Debt 89 / Sale 76
- Debt 102 / Sale 90

No application data write was executed. The backup command used `pg_dump` and did not modify financial rows. The failed baseline command did not start because its script file was missing.

## 11. Final Production State

The final state is the last successfully observed pre-deployment state:

| Item | Result |
|---|---|
| Deployed Git commit | `f50e6ab3dc7d4b6452d94e5cd72cf427c45d4fd5` |
| Branch | `agent/fix-catalog-permanent-deletion` |
| Backend | Running and healthy |
| Nginx/public site | Running; public HTTP 200 |
| PostgreSQL | Running and healthy |
| Migration ledger | Absent before baseline; still absent because baseline did not run |
| Historical migration replay | None |
| Fresh backup | Available and gzip-verified |
| Deployment | Not started |
| Protected historical pairs | Preserved |

## 12. Remaining Risks

1. The production checkout lacks `/opt/sonograma/app/deploy/baseline-migrations.sh`, while the local release gate includes that script.
2. The production migration ledger is absent, so the normal deploy must not proceed until an authorized, reviewed baseline workflow is available on production.
3. The local worktree is dirty. Its completed changes are not automatically present in the production checkout and must not be copied to production as an improvised workaround.
4. The five historical Venta/Deuda divergences remain intentionally untouched.

Recommended next action: reconcile the production checkout with the reviewed release artifact through the normal authorized release process, verify the baseline script is present and matches the reviewed checksum, then restart the rollout from the production preflight. Do not manually copy the script, create the ledger by ad-hoc SQL, execute migrations, or rerun deployment until that release discrepancy is resolved.

## 13. Final Result

FAILED PHASE: Phase 4 — Migration Baseline

LAST SUCCESSFUL CHECKPOINT: Fresh production backup completed and verified at `/opt/sonograma/backups/sonograma_db_20260914_203851.sql.gz`.

PRODUCTION SERVICES CURRENT STATUS: Last observed pre-deployment state — PostgreSQL healthy, backend healthy, Nginx running, public site HTTP 200, API health `UP`.

DATABASE WRITE STATUS: No application financial writes, baseline writes, migration writes, or ledger writes occurred. The backup was read-only with respect to database data.

BACKUP AVAILABLE: Yes — `/opt/sonograma/backups/sonograma_db_20260914_203851.sql.gz`, 799,851 bytes, `gzip -t` passed.

RECOMMENDED NEXT ACTION: Resolve the missing production baseline script through the normal controlled release process, verify its integrity, and restart from Phase 2. Do not bypass the baseline gate.

FAIL — PRODUCTION ROLLOUT STOPPED SAFELY
