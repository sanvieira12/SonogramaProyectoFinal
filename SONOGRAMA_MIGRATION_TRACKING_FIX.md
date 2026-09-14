# Sonograma Migration Tracking Fix

## 1. Existing Risk

The previous deployment script replayed every file under `docs/migraciones/*.sql` on every deployment. Although the SQL command used `ON_ERROR_STOP=1`, the old shell fallback treated a migration failure as a warning and continued. More importantly, the migration collection contains historical DDL, updates, inserts, backfills, and conditional deletion logic that is not a uniformly idempotent migration system.

The current checkout contains 50 SQL files through numeric prefix `047`. The count is 50 rather than 47 because prefixes `010`, `021`, and `025` each have two distinct full filenames.

## 2. Migration Ledger Design

Added the PostgreSQL table `sonograma_schema_migrations`:

```sql
filename   VARCHAR(255) PRIMARY KEY
applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
checksum   VARCHAR(64) NOT NULL
```

Checksums are SHA-256. The full filename is the identity, not the numeric prefix, so duplicate prefixes cannot collide. Normal deployment never creates or silently initializes this table; it requires an existing valid ledger.

No Flyway or Liquibase framework was introduced because neither was in use for this production migration path.

## 3. Baseline Strategy

Added `deploy/baseline-migrations.sh`. It requires the explicit flag:

```bash
./deploy/baseline-migrations.sh --confirm-production-baseline
```

The script:

- requires the environment file, Docker, PostgreSQL, and the expected migration directory;
- creates the ledger table with guarded DDL;
- computes checksums for all 50 files with prefixes through `047`;
- records missing historical rows without reading or executing their SQL contents;
- aborts if an existing ledger row has a different checksum;
- safely skips matching existing rows on repeat execution;
- verifies that all 50 expected historical files are present.

The baseline is not called by normal deployment. It must be run only after the documented read-only production verification and a fresh backup. Migrations `001`–`047` must not be replayed merely to populate the ledger.

## 4. Deploy Script Changes

`deploy/deploy.sh` now:

- supports local-safe `APP_DIR`, `ENV_FILE`, and `DATA_DIR` overrides while retaining production defaults;
- supports `sha256sum` and falls back to `shasum -a 256` where needed;
- requires the PostgreSQL container and fails if it is unavailable;
- requires the migration ledger table and fails clearly if the baseline is missing;
- validates every historical file through `047` against its ledger checksum before any migration execution;
- tracks by full filename, preserving duplicate numeric prefixes;
- skips an existing same-checksum migration;
- executes only an absent migration file with `psql -v ON_ERROR_STOP=1`;
- records a migration only after its SQL command succeeds;
- aborts before frontend build, backend image build, service stop, or service replacement on migration, ledger, baseline, or checksum failure.

The previous replay-all behavior and the “failed or already applied, continuing” warning path are gone.

## 5. Checksum Protection

For each file, deployment computes a local SHA-256 checksum and compares it with the ledger row for the exact filename.

The outcomes are:

```text
missing filename       → execute SQL, then insert filename/checksum/timestamp
same filename/checksum → skip SQL
same filename/different checksum → abort with integrity error
```

A checksum drift error identifies the filename and states that deployment was aborted. Historical files were not renamed or modified.

## 6. Failure Semantics

`psql -v ON_ERROR_STOP=1` stops a migration at the first SQL error. The shell then emits a fatal error and returns non-zero. The loop does not continue to the next migration, and the later build/replacement stages are unreachable.

The SQL execution and ledger insert are intentionally separate commands. If SQL succeeds but the ledger insert fails, deployment aborts and the migration is not recorded. That leaves a possible applied-but-unrecorded migration requiring operator review before retry; arbitrary historical files were not wrapped in an unverified transaction.

The migration files themselves are not universally transactional. A file can still partially commit before a later statement fails. This remains documented rather than being masked by an unsafe blanket transaction wrapper.

## 7. Mock Validation

Added `deploy/test-migration-tracking.sh`, which runs the actual baseline and deploy scripts against temporary copied migration files and a fake Docker/CLI layer. It does not connect to PostgreSQL or production.

The following scenarios passed:

1. Baseline: all 50 historical filenames were recorded and no historical SQL was executed.
2. Repeat baseline: matching rows were skipped without duplicate records or SQL execution.
3. Same migration applied: normal deployment skipped all recorded migrations and reached the continuation path.
4. New migration: fake `048` executed once, was recorded, and was skipped on the second deployment.
5. SQL failure: fake `049` returned failure, was not recorded, fake `050` did not run, and no service replacement occurred.
6. Checksum drift: a modified historical file aborted before execution and service replacement.
7. Missing ledger/baseline: normal deployment aborted clearly before migration execution and service replacement.

Additional checks passed:

- `bash -n deploy/deploy.sh`
- `bash -n deploy/baseline-migrations.sh`
- `bash -n deploy/test-migration-tracking.sh`
- `git diff --check`

ShellCheck was not installed on this development machine.

## 8. Operational First-Deploy Procedure

After this change, the first production update must follow this order:

1. Perform the existing production read-only verification, including the verified checkout and schema state through `047`.
2. Create a fresh backup with `/opt/sonograma/app/deploy/backup-db.sh`.
3. Run the explicit baseline command:
   `/opt/sonograma/app/deploy/baseline-migrations.sh --confirm-production-baseline`
4. Verify the ledger with a SELECT-only query and confirm 50 rows with matching checksums. The extra rows reflect duplicate full filenames under prefixes `010`, `021`, and `025`.
5. Run the normal `/opt/sonograma/app/deploy/deploy.sh`.
6. Confirm backend, Nginx, and public application health checks.

The baseline command does not execute migrations `001`–`047`; it only records their canonical filename/checksum state after the operator has verified production.

## 9. Files Changed

- `deploy/deploy.sh`
- `deploy/baseline-migrations.sh`
- `deploy/test-migration-tracking.sh`
- `deploy/README-LIGHTSAIL.md`
- `SONOGRAMA_MIGRATION_TRACKING_FIX.md`

No financial, Venta/Deuda, payment, catalogue, stock, authentication, or production-data logic was changed. Existing unrelated worktree changes were preserved.

## 10. Remaining Risks

- The baseline is an explicit operator action and must not be run without confirming the production schema state and taking a fresh backup.
- SQL execution and ledger recording are not atomically coupled; a ledger insert failure after successful SQL requires operator review.
- Historical migrations can partially commit because universal transactional safety was not established.
- There is no automatic schema rollback or application rollback after a post-deploy health failure.
- Future migration filenames should use a new numeric prefix after `047` and must never modify an already-recorded file.
- `docs/DEPLOY.md` still describes the former Railway/Vercel architecture; the active Lightsail sequence is documented in `deploy/README-LIGHTSAIL.md`.

## 11. Final Result

Normal deployment now requires a valid baseline, verifies full filename/checksum identity, skips recorded migrations, executes only genuinely new files, records them after success, and fails closed before service replacement on migration or governance errors.

PASS — MIGRATIONS ARE TRACKED AND HISTORICAL SQL IS NOT REPLAYED
