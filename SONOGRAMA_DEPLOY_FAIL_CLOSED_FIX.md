# Sonograma Deployment Fail-Closed Fix

## 1. Existing Risk

`deploy/deploy.sh` already invoked `psql -v ON_ERROR_STOP=1`, but wrapped the migration command in:

```bash
|| warn "Migración ... falló o ya estaba aplicada, continuando..."
```

That made an actual SQL failure indistinguishable from an already-applied migration and allowed frontend build, backend image replacement, service stop/start, and application startup to continue against an uncertain schema.

## 2. Current Deployment Flow

The current Lightsail script flow is:

```text
preflight → backup → git fetch/checkout/pull → PostgreSQL gate → SQL migrations
→ legacy-expense validation → frontend build → backend Docker build
→ service replacement → backend health check → Nginx/application health checks
```

The database migration phase occurs before any `docker compose stop` or `docker compose up`. The code pull occurs before migrations, so a failed migration can leave the checkout updated, but it cannot replace or restart the application services.

The 47 migration files are selected by the Bash glob `docs/migraciones/*.sql` in lexical filename order:

```text
001_cantidad_copias.sql
002_detalle_venta.sql
003_shipping_order_costos.sql
004_pedidos.sql
005_clientes_importacion_excel.sql
006_catalog_audio_preview.sql
007_discogs_import_jobs.sql
008_pedidos_invoice_metadata.sql
009_pedido_item_page_data.sql
010_deudor.sql
010_discogs_catalog_number.sql
011_qr_copies_and_youtube_previews.sql
012_management_refactor_clientes_deudas_pedidos_notas.sql
013_deudas_activas_tipo_cambio_50.sql
014_vinylfuture_async_import_cost_audit.sql
015_discogs_import_excel_metadata.sql
016_manual_sale_items_and_client_cleanup.sql
017_pricing_settings_and_catalog_mode.sql
018_preventas_gastos_copy_snapshot.sql
019_stock_selected_scope_and_manual_markup.sql
020_pricing_decimal_precision.sql
021_preventa_payments_disc_codes.sql
021_stock_source_shipping_backfill.sql
022_pedidos_vinylfuture_source.sql
023_backfill_vinylfuture_pedidos.sql
024_pedidos_invoice_line_fidelity.sql
025_catalog_status_from_copy_inventory.sql
025_venta_numero_recibo.sql
026_discogs_import_observation.sql
027_discogs_import_fingerprint.sql
028_discogs_import_extra_columns.sql
029_deudas_balance_por_movimiento.sql
030_pago_deuda_recibo_idempotencia.sql
031_pago_deuda_anulacion.sql
032_costo_adquisicion_historico_detalle_venta.sql
033_detalle_venta_costo_uyu.sql
034_gasto_tienda_categoria.sql
035_dac_branch_customer_persistence.sql
036_gasto_tienda_categoria_legacy.sql
037_catalog_permanent_deletion.sql
038_crm_interes_cliente.sql
039_discogs_import_stage_and_zip_progress.sql
040_discogs_physical_condition.sql
041_discogs_catalog_items_are_used.sql
042_vinylfuture_pdf_validation.sql
043_vinylfuture_identity_and_idempotency.sql
044_discogs_canonical_release_identity.sql
045_manual_discogs_import_operation.sql
046_discogs_bulk_reconciliation.sql
047_discogs_manual_batches.sql
```

## 3. Migration Execution Analysis

The script replays every matching SQL file on every deployment; it does not select only pending migrations.

Many additive schema and index statements use `IF NOT EXISTS`. Several data backfills are guarded by null, existence, or state predicates and are intended to converge on replay. However, the complete set is not uniformly idempotent as a migration system:

- Some DDL is unconditional, including `ALTER COLUMN ... DROP NOT NULL`, `ALTER COLUMN ... SET NOT NULL`, and type changes.
- Several files write existing data with `UPDATE`, including legacy normalization, catalog status, debt balance, purchase-cost, and import backfills.
- Insert/backfill migrations exist in `002`, `011`, `017`, and `023`; their guards reduce duplicate risk but do not constitute migration history.
- `011` contains a conditional dynamic `DELETE` for QR-copy rows associated with catalog tombstones.
- `029` recalculates historical debt caches and predates the later annulment column, so replaying the historical file after later schema/data changes is not equivalent to applying it once at its original point.

The migration files contain no `BEGIN`/`COMMIT` wrapper and the deploy command does not use `--single-transaction`. PostgreSQL therefore may commit earlier statements in a file before a later statement fails. This is a remaining migration-design risk; this phase does not blindly add a transaction wrapper because transactional compatibility was not established for every historical migration.

## 4. Migration Tracking Analysis

No Flyway metadata, Liquibase metadata, custom schema-history table, filename-tracking table, or other migration ledger was found in the deployment scripts, compose file, deployment documentation, or `docs/migraciones/`.

The smallest compatible follow-up is a deliberate migration-history policy: baseline the already-applied production state, then record and apply newly approved migration files once. That should be designed and tested separately because automatically introducing a ledger now would need to distinguish historical files already applied from files still pending.

## 5. Implemented Fail-Closed Behavior

In `deploy/deploy.sh`:

- Retained `psql -v ON_ERROR_STOP=1`.
- Replaced the warning fallback with an explicit `if ! ...; then die ...; fi` gate.
- A failed file prints `Migration <filename> failed. Deployment aborted.` and exits non-zero.
- The loop stops immediately; later migration files are not executed.
- The PostgreSQL container is now required. If `sonograma-postgres` is not running, the deploy aborts before build or service replacement rather than skipping migrations.
- The prior legacy-expense validation remains fatal.

There is no path from a migration failure to frontend build, backend image build, service stop, service replacement, or restart.

## 6. Backup Gate

The script invokes `deploy/backup-db.sh` before the code pull and migration phase and terminates on backup failure:

```bash
"$APP_DIR/deploy/backup-db.sh" || die "Backup falló; deploy cancelado sin modificar servicios."
```

`backup-db.sh` keeps the existing 14-day retention policy. Its `set -euo pipefail`, non-empty archive check, and gzip integrity check provide the pre-mutation backup gate. No production backup was created during this code-change task.

## 7. Service Replacement Safety

The script does not stop or replace `backend` or `nginx` until after the backup, PostgreSQL availability check, all migration files, and the legacy-expense validation succeed. `docker compose stop` and `docker compose up -d --remove-orphans` remain later deployment steps.

No deployment, SSH production write, migration, container restart, or production database write was executed during this task.

## 8. Health Check Behavior

The existing backend health loop continues to poll `/api/actuator/health` inside `sonograma-backend` for up to 120 seconds. On timeout it prints recent backend logs and exits non-zero.

The final verification now additionally:

- validates the Nginx configuration with `nginx -t`, printing recent Nginx logs on failure;
- requests `/api/actuator/health` through the local HTTPS Nginx endpoint using `curl --resolve`, failing clearly if the proxy/application path is unavailable.

Health-check failure stops the script with a non-zero result. There is no automatic rollback of the newly built image or database schema; the available recovery mechanism remains the pre-deploy database backup and the existing manual restore/deployment rollback procedures.

## 9. Shell Safety

`deploy.sh` and `backup-db.sh` use `set -euo pipefail`. The migration command is now handled in an explicit conditional, so its expected failure branch is controlled and cannot be masked by `|| warn`. Existing intentional non-zero handling remains explicit (`2>/dev/null || true` for optional stop/listing operations and the JSON-formatting fallback).

ShellCheck was not available on this development machine. `bash -n deploy/deploy.sh` and `bash -n deploy/backup-db.sh` passed.

## 10. Validation Tests

The real deployment script was not executed. A controlled local mock exercised the equivalent migration gate with three temporary dummy files:

- Scenario A: all three mocked migrations succeed; continuation is allowed.
- Scenario B: migration 1 fails; the gate returns non-zero and stops.
- Scenario C: migration 2 fails after migration 1 succeeds; the gate returns non-zero and migration 3 is not executed.

Static assertions also confirmed that `ON_ERROR_STOP=1` and the fatal error message are present and that the former “failed or already applied, continuing” warning is absent.

Additional validation:

- `bash -n deploy/deploy.sh` — passed.
- `bash -n deploy/backup-db.sh` — passed.
- `git diff --check` — passed.
- ShellCheck — unavailable, not a code failure.

## 11. Files Changed

- `deploy/deploy.sh`
- `deploy/README-LIGHTSAIL.md`
- `SONOGRAMA_DEPLOY_FAIL_CLOSED_FIX.md`

No financial, debt/payment, Venta/Deuda, catalogue, import, authentication, or production-data code was changed.

## 12. Remaining Deployment Risks

- There is no migration-history ledger; the script still replays all historical SQL files on every deployment.
- The migration collection is not uniformly idempotent and includes data writes/backfills.
- A migration can partially commit before a later statement fails because the files are not universally wrapped in verified transactions.
- There is no automatic schema rollback or automatic application rollback after a post-deploy health failure.
- `docs/DEPLOY.md` still describes the former Railway/Vercel deployment architecture; the active Lightsail guidance is in `deploy/README-LIGHTSAIL.md`.

These risks are documented for a separate migration-governance phase. No large migration framework rewrite or automatic workaround was introduced here.

## 13. Final Result

The deployment now fails closed on SQL migration failure or an unavailable PostgreSQL migration target, and it cannot proceed to service replacement after either condition. Backup gating and backend/Nginx application health checks are explicit. The real deployment was not run.

PASS — DEPLOYMENT FAILS CLOSED ON MIGRATION ERROR
