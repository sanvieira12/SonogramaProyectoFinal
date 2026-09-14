# Sonograma Baseline Script Fix

## 1. Root Cause

The baseline and deploy ledger paths supplied SQL through PostgreSQL's `-c` option while using psql variables inside the SQL as `:'migration_filename'` and `:'migration_checksum'`.

In this execution path, psql did not perform the variable substitution for the `-c` command string. The literal token `:'migration_filename'` therefore reached PostgreSQL, which reported a syntax error at `:`. The same defect affected both the baseline lookup/insert path and the normal deploy ledger lookup/insert path.

## 2. Affected Code Paths

The audit covered the scripts under `deploy/`:

- `deploy/baseline-migrations.sh`: historical ledger checksum lookup and baseline row insertion.
- `deploy/deploy.sh`: `read_ledger_checksum` and `record_migration`, used for historical validation and new-migration tracking.
- `deploy/test-migration-tracking.sh`: existing mock ledger paths and the new real PostgreSQL regression path.
- `deploy/clear-discs-catalog.sh`: uses psql variables in SQL read from a heredoc and does not use the affected `-c` interpolation pattern.
- Other `deploy/` psql invocations either execute fixed SQL or migration-file input and did not contain the affected variable pattern.

## 3. Fix

Changed only the migration-tracking invocation in `deploy/baseline-migrations.sh` and `deploy/deploy.sh`:

- SQL is now supplied through stdin using a quoted heredoc.
- `docker exec -i` preserves the SQL input stream.
- Filename and checksum values are passed as separate, quoted psql variable assignments: `-v "migration_filename=$value"` and `-v "migration_checksum=$value"`.
- SQL uses psql's quoted-literal forms `:'migration_filename'` and `:'migration_checksum'`.
- `ON_ERROR_STOP=1` remains enabled for writes.
- Existing checksum mismatch, missing-ledger, and fail-closed behavior is unchanged.

This safely supports underscores, hyphens, and dots in filenames without shell or SQL string concatenation.

## 4. Empty Existing Ledger Compatibility

The baseline still uses `CREATE TABLE IF NOT EXISTS` and does not drop, truncate, or recreate the ledger.

The regression test creates an already-existing empty `sonograma_schema_migrations` table, then runs the real baseline path. It records exactly 50 historical filenames and checksums, executes zero historical SQL files, and passes on a repeat run with all 50 rows recognized as already registered.

## 5. Regression Tests

`deploy/test-migration-tracking.sh` now includes an ephemeral local PostgreSQL execution path when `initdb`, `pg_ctl`, `postgres`, and `psql` are available. The test forwards the same production ledger lookup/insert invocations through the Docker test shim to the real local PostgreSQL server.

The real-path checks cover:

1. empty existing ledger accepted;
2. missing migration lookup and insertion;
3. existing migration lookup on repeat baseline;
4. stored checksum comparison;
5. filename containing underscores, a hyphen, and dots;
6. deploy ledger validation path;
7. SQL failure abort behavior;
8. checksum drift abort behavior.

The existing mock scenarios remain in place for baseline repeat/skip behavior, new migration handling, migration SQL failure, checksum drift, missing ledger, and service-continuation gates.

## 6. Validation Results

All requested validations passed:

| Check | Result |
|---|---|
| `bash -n deploy/baseline-migrations.sh` | PASS |
| `bash -n deploy/deploy.sh` | PASS |
| `bash -n deploy/test-migration-tracking.sh` | PASS |
| Real PostgreSQL interpolation path | PASS |
| Deploy ledger path against real PostgreSQL | PASS |
| `./deploy/test-migration-tracking.sh` | PASS |
| `git diff --check` | PASS |

The test confirmed 50 historical rows, no historical SQL replay, repeat-baseline idempotence, special filename handling, checksum validation, SQL failure abort, and checksum-drift abort.

## 7. Production Safety

This task made no production connection and performed no production write. It did not execute the production baseline, deploy, migrations, restart, rebuild, ledger modification, or financial-data operation.

This task changed only the two migration-tracking scripts, the migration-tracking regression test, and this report. Unrelated dirty-worktree files were not staged, committed, or modified.

## 8. Next Step

Review the three deployment/migration-tracking code changes, commit only the reviewed fix scope, push it through the normal release workflow, and then restart the production rollout from its preflight. Production baseline and deploy remain unexecuted until that reviewed release is synchronized.

PASS — BASELINE SCRIPT FIXED AND VERIFIED LOCALLY
