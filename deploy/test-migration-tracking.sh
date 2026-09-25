#!/bin/bash

set -euo pipefail

SCRIPT_PATH=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")
ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

fail() {
    echo "TEST FAILURE: $1" >&2
    exit 1
}

count_lines() {
    local pattern="$1"
    local file="$2"
    grep -c "$pattern" "$file" 2>/dev/null || true
}

if [ "$(basename "$0")" = "docker" ]; then
    state_file=${MOCK_STATE:?MOCK_STATE is required}
    log_file=${MOCK_LOG:?MOCK_LOG is required}
    command_name=${1:-}

    log_mock() {
        printf '%s\n' "$1" >> "$log_file"
    }

    migration_value() {
        local key="$1"
        local argument
        for argument in "$@"; do
            case "$argument" in
                "$key"=*) printf '%s' "${argument#*=}"; return 0 ;;
            esac
        done
        return 1
    }

    ledger_checksum() {
        local filename="$1"
        awk -F'|' -v filename="$filename" '$1 == "M" && $2 == filename { print $3; exit }' "$state_file"
    }

    case "$command_name" in
        ps)
            printf '%s\n' sonograma-postgres
            exit 0
            ;;
        compose)
            log_mock "COMPOSE|$*"
            exit 0
            ;;
        logs)
            exit 0
            ;;
        exec)
            if [ "${REAL_PG_LEDGER:-0}" = "1" ]; then
                has_migration_variable=0
                for argument in "$@"; do
                    case "$argument" in
                        migration_filename=*) has_migration_variable=1; break ;;
                    esac
                done
                if [ "$has_migration_variable" -eq 1 ]; then
                    shift
                    [ "${1:-}" = -i ] && shift
                    [ "${1:-}" = sonograma-postgres ] || exit 1
                    shift
                    [ "${1:-}" = -i ] && shift
                    [ "${1:-}" = psql ] || exit 1
                    shift
                    printf '%s\n' REAL_PSQL_LEDGER >> "$log_file"
                    exec "$REAL_PG_BIN/psql" "$@"
                fi
            fi
            command_line="$*"

            if [[ "$command_line" == *"sonograma-nginx"* && "$command_line" == *"nginx -t"* ]]; then
                exit 0
            fi

            if [[ "$command_line" == *"to_regclass"* ]]; then
                if grep -Fqx 'TABLE' "$state_file" 2>/dev/null; then
                    printf '%s\n' sonograma_schema_migrations
                fi
                exit 0
            fi

            if [[ "$command_line" == *"sonograma-postgres"* && "$command_line" != *" -c "* ]]; then
                input=$(cat)

                if [[ "$input" == *"CREATE TABLE IF NOT EXISTS sonograma_schema_migrations"* ]]; then
                    if ! grep -Fqx 'TABLE' "$state_file" 2>/dev/null; then
                        printf '%s\n' TABLE >> "$state_file"
                    fi
                    log_mock CREATE_LEDGER
                    exit 0
                fi

                if [[ "$input" == *"SELECT checksum FROM sonograma_schema_migrations"* ]]; then
                    filename=$(migration_value migration_filename "$@") || exit 1
                    ledger_checksum "$filename"
                    exit 0
                fi

                if [[ "$input" == *"INSERT INTO sonograma_schema_migrations"* ]]; then
                    filename=$(migration_value migration_filename "$@") || exit 1
                    checksum=$(migration_value migration_checksum "$@") || exit 1
                    printf 'M|%s|%s\n' "$filename" "$checksum" >> "$state_file"
                    log_mock "RECORD|$filename"
                    exit 0
                fi

                filename=$(printf '%s\n' "$input" | sed -n 's/^-- MOCK_MIGRATION_NAME=//p' | head -n 1)
                filename=${filename:-unknown}
                log_mock "SQL|$filename"
                if [ "${MOCK_FAIL_SQL:-0}" = "1" ]; then
                    exit 1
                fi
                exit 0
            fi

            if [[ "$command_line" == *"SELECT checksum"* ]]; then
                filename=$(migration_value migration_filename "$@") || exit 1
                ledger_checksum "$filename"
                exit 0
            fi

            if [[ "$command_line" == *"CREATE TABLE IF NOT EXISTS sonograma_schema_migrations"* ]]; then
                if ! grep -Fqx 'TABLE' "$state_file" 2>/dev/null; then
                    printf '%s\n' TABLE >> "$state_file"
                fi
                log_mock CREATE_LEDGER
                exit 0
            fi

            if [[ "$command_line" == *"INSERT INTO sonograma_schema_migrations"* ]]; then
                filename=$(migration_value migration_filename "$@") || exit 1
                checksum=$(migration_value migration_checksum "$@") || exit 1
                printf 'M|%s|%s\n' "$filename" "$checksum" >> "$state_file"
                log_mock "RECORD|$filename"
                exit 0
            fi

            if [[ "$command_line" == *"SELECT COUNT(*) FROM gasto_tienda"* ]]; then
                printf '%s\n' 0
                exit 0
            fi

            if [[ "$command_line" == *"sonograma-postgres"* && "$command_line" != *" -c "* ]]; then
                input=$(cat)
                filename=$(printf '%s\n' "$input" | sed -n 's/^-- MOCK_MIGRATION_NAME=//p' | head -n 1)
                filename=${filename:-unknown}
                log_mock "SQL|$filename"
                if [ "${MOCK_FAIL_SQL:-0}" = "1" ]; then
                    exit 1
                fi
                exit 0
            fi

            exit 0
            ;;
        *)
            exit 0
            ;;
    esac
fi

case "$(basename "$0")" in
    backup-db.sh)
        exit 0
        ;;
    git)
        if [ "${1:-}" = "log" ]; then
            printf '%s\n' mock-commit
        fi
        exit 0
        ;;
    npm|node|curl)
        exit 0
        ;;
esac

TEST_ROOT=$(mktemp -d)
MOCK_BIN="$TEST_ROOT/bin"
APP_DIR="$TEST_ROOT/app"
STATE_FILE="$TEST_ROOT/ledger.state"
LOG_FILE="$TEST_ROOT/mock.log"
MISSING_STATE="$TEST_ROOT/missing.state"
EMPTY_BASELINE_STATE="$TEST_ROOT/empty-baseline.state"

mkdir -p "$MOCK_BIN" "$APP_DIR/.git" "$APP_DIR/deploy" "$APP_DIR/frontend" "$APP_DIR/docs/migraciones" "$APP_DIR/data"
: > "$STATE_FILE"
: > "$LOG_FILE"
: > "$MISSING_STATE"
printf '%s\n' TABLE > "$EMPTY_BASELINE_STATE"
cp "$ROOT_DIR"/docs/migraciones/*.sql "$APP_DIR/docs/migraciones/"
touch "$APP_DIR/env"
ln -s "$SCRIPT_PATH" "$MOCK_BIN/docker"
ln -s "$SCRIPT_PATH" "$MOCK_BIN/backup-db.sh"
ln -s "$SCRIPT_PATH" "$MOCK_BIN/git"
ln -s "$SCRIPT_PATH" "$MOCK_BIN/node"
ln -s "$SCRIPT_PATH" "$MOCK_BIN/npm"
ln -s "$SCRIPT_PATH" "$MOCK_BIN/curl"
ln -s "$SCRIPT_PATH" "$APP_DIR/deploy/backup-db.sh"

find_real_postgres_bin() {
    local bindir
    if command -v pg_config >/dev/null 2>&1; then
        bindir=$(pg_config --bindir 2>/dev/null || true)
        if [ -x "$bindir/initdb" ] && [ -x "$bindir/pg_ctl" ] && \
            [ -x "$bindir/postgres" ] && [ -x "$bindir/psql" ]; then
            printf '%s\n' "$bindir"
            return 0
        fi
    fi

    for bindir in /opt/homebrew/opt/postgresql@16/bin /usr/local/opt/postgresql@16/bin; do
        if [ -x "$bindir/initdb" ] && [ -x "$bindir/pg_ctl" ] && \
            [ -x "$bindir/postgres" ] && [ -x "$bindir/psql" ]; then
            printf '%s\n' "$bindir"
            return 0
        fi
    done
    return 1
}

REAL_PG_BIN=$(find_real_postgres_bin || true)

real_psql() {
    local database="${1:-sonograma_db}"
    shift || true
    "$REAL_PG_BIN/psql" -X -Atq -h "$REAL_PG_SOCKET" -p "$REAL_PG_PORT" \
        -U sonograma_test -d "$database" "$@"
}

run_real_baseline() {
    PATH="$MOCK_BIN:$PATH" \
        MOCK_STATE="$REAL_STATE" \
        MOCK_LOG="$REAL_LOG" \
        REAL_PG_LEDGER=1 \
        REAL_PG_BIN="$REAL_PG_BIN" \
        PGHOST="$REAL_PG_SOCKET" \
        PGPORT="$REAL_PG_PORT" \
        APP_DIR="$REAL_APP_DIR" \
        ENV_FILE="$REAL_APP_DIR/env" \
        "$ROOT_DIR/deploy/baseline-migrations.sh" --confirm-production-baseline
}

run_real_deploy() {
    PATH="$MOCK_BIN:$PATH" \
        MOCK_STATE="$REAL_STATE" \
        MOCK_LOG="$REAL_LOG" \
        REAL_PG_LEDGER=1 \
        REAL_PG_BIN="$REAL_PG_BIN" \
        PGHOST="$REAL_PG_SOCKET" \
        PGPORT="$REAL_PG_PORT" \
        APP_DIR="$REAL_APP_DIR" \
        ENV_FILE="$REAL_APP_DIR/env" \
        DATA_DIR="$REAL_APP_DIR/data" \
        "$ROOT_DIR/deploy/deploy.sh"
}

run_real_postgres_tracking_test() {
    [ -n "$REAL_PG_BIN" ] || return 2

    REAL_PG_ROOT=$(mktemp -d)
    REAL_PG_DATA="$REAL_PG_ROOT/data"
    REAL_PG_SOCKET="$REAL_PG_ROOT/socket"
    REAL_PG_PORT=$((55000 + ($$ % 1000)))
    REAL_APP_DIR="$REAL_PG_ROOT/app"
    REAL_STATE="$REAL_PG_ROOT/ledger.state"
    REAL_LOG="$REAL_PG_ROOT/mock.log"

    mkdir -p "$REAL_PG_SOCKET" "$REAL_APP_DIR/.git" "$REAL_APP_DIR/deploy" \
        "$REAL_APP_DIR/frontend" "$REAL_APP_DIR/docs/migraciones" "$REAL_APP_DIR/data"
    ln -s "$SCRIPT_PATH" "$REAL_APP_DIR/deploy/backup-db.sh"
    cp "$APP_DIR"/docs/migraciones/*.sql "$REAL_APP_DIR/docs/migraciones/"
    mv "$REAL_APP_DIR/docs/migraciones/047_discogs_manual_batches.sql" \
        "$REAL_APP_DIR/docs/migraciones/047_file-name.with_underscores.sql"
    printf '%s\n' 'SPRING_DATASOURCE_USERNAME=sonograma_test' > "$REAL_APP_DIR/env"
    printf '%s\n' TABLE > "$REAL_STATE"
    : > "$REAL_LOG"

    "$REAL_PG_BIN/initdb" -D "$REAL_PG_DATA" -A trust -U sonograma_test \
        > "$REAL_PG_ROOT/initdb.log"
    "$REAL_PG_BIN/pg_ctl" -D "$REAL_PG_DATA" \
        -o "-k $REAL_PG_SOCKET -p $REAL_PG_PORT" -w start \
        > "$REAL_PG_ROOT/pg_ctl-start.log" 2>&1
    real_psql postgres <<'SQL'
CREATE DATABASE sonograma_db;
SQL
    real_psql sonograma_db <<'SQL'
CREATE TABLE sonograma_schema_migrations (
    filename VARCHAR(255) PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    checksum VARCHAR(64) NOT NULL CHECK (checksum ~ '^[0-9a-f]{64}$')
);
SQL
    [ "$(real_psql sonograma_db -c 'SELECT COUNT(*) FROM sonograma_schema_migrations;')" = 0 ] || \
        fail 'real PostgreSQL test ledger was not initially empty'

    run_real_baseline
    [ "$(real_psql sonograma_db -c 'SELECT COUNT(*) FROM sonograma_schema_migrations;')" = 50 ] || \
        fail 'real PostgreSQL baseline did not record exactly 50 rows'
    grep -Fqx '047_file-name.with_underscores.sql' <(real_psql sonograma_db -c 'SELECT filename FROM sonograma_schema_migrations WHERE filename LIKE '\''047_%'\'';') || \
        fail 'real PostgreSQL baseline did not preserve the special filename'

    second_output=$(run_real_baseline)
    [ "$(real_psql sonograma_db -c 'SELECT COUNT(*) FROM sonograma_schema_migrations;')" = 50 ] || \
        fail 'real PostgreSQL repeat baseline changed row count'
    [ "$(printf '%s\n' "$second_output" | grep -c 'Baseline ya registrado:')" -eq 50 ] || \
        fail 'real PostgreSQL repeat baseline did not find all existing rows'

    expected_checksum=$(sha256sum "$REAL_APP_DIR/docs/migraciones/047_file-name.with_underscores.sql" | awk '{print $1}')
    stored_checksum=$(real_psql sonograma_db -c "SELECT checksum FROM sonograma_schema_migrations WHERE filename = '047_file-name.with_underscores.sql';")
    [ "$stored_checksum" = "$expected_checksum" ] || fail 'real PostgreSQL checksum comparison failed'

    run_real_deploy
    [ "$(grep -c '^REAL_PSQL_LEDGER$' "$REAL_LOG")" -gt 0 ] || \
        fail 'deploy ledger path did not reach real PostgreSQL'

    printf '\n-- real checksum drift --\n' >> "$REAL_APP_DIR/docs/migraciones/047_file-name.with_underscores.sql"
    if run_real_baseline > "$REAL_PG_ROOT/drift.log" 2>&1; then
        fail 'real PostgreSQL checksum drift returned success'
    fi
    grep -Fq 'different checksum in the ledger' "$REAL_PG_ROOT/drift.log" || \
        fail 'real PostgreSQL checksum drift did not abort with the expected error'
}

if [ -n "$REAL_PG_BIN" ]; then
    run_real_postgres_tracking_test
else
    printf '%s\n' 'Real PostgreSQL tracking test skipped: initdb, pg_ctl, and psql were not available.'
fi

run_baseline() {
    PATH="$MOCK_BIN:$PATH" \
        MOCK_STATE="$STATE_FILE" \
        MOCK_LOG="$LOG_FILE" \
        APP_DIR="$APP_DIR" \
        ENV_FILE="$APP_DIR/env" \
        "$ROOT_DIR/deploy/baseline-migrations.sh" --confirm-production-baseline
}

run_deploy() {
    local state_file="$1"
    shift
    PATH="$MOCK_BIN:$PATH" \
        MOCK_STATE="$state_file" \
        MOCK_LOG="$LOG_FILE" \
        APP_DIR="$APP_DIR" \
        ENV_FILE="$APP_DIR/env" \
        DATA_DIR="$APP_DIR/data" \
        "$@" "$ROOT_DIR/deploy/deploy.sh"
}

run_baseline
[ "$(count_lines '^SQL|' "$LOG_FILE")" -eq 0 ] || fail 'baseline executed historical SQL'
[ "$(count_lines '^M|.*' "$STATE_FILE")" -eq 50 ] || fail 'baseline did not record all 50 historical files'

run_baseline
[ "$(count_lines '^SQL|' "$LOG_FILE")" -eq 0 ] || fail 'repeat baseline executed historical SQL'
[ "$(count_lines '^RECORD|' "$LOG_FILE")" -eq 50 ] || fail 'repeat baseline inserted duplicate records'

before_compose=$(count_lines '^COMPOSE|' "$LOG_FILE")
pending_migrations=0
for migration in "$APP_DIR"/docs/migraciones/*.sql; do
    [ -f "$migration" ] || continue
    prefix=$(basename "$migration" | cut -d_ -f1)
    if [[ "$prefix" =~ ^[0-9]{3}$ ]] && (( 10#$prefix > 47 )); then
        pending_migrations=$((pending_migrations + 1))
    fi
done
run_deploy "$STATE_FILE" env
[ "$(count_lines '^SQL|' "$LOG_FILE")" -eq "$pending_migrations" ] || fail 'pending migrations were not applied exactly once'
[ "$(count_lines '^COMPOSE|' "$LOG_FILE")" -gt "$before_compose" ] || fail 'successful deployment did not reach continuation'

before_sql=$(count_lines '^SQL|' "$LOG_FILE")
run_deploy "$STATE_FILE" env
[ "$(count_lines '^SQL|' "$LOG_FILE")" -eq "$before_sql" ] || fail 'already-applied migrations were replayed'

cp "$APP_DIR/docs/migraciones/047_discogs_manual_batches.sql" "$APP_DIR/docs/migraciones/048_mock_new.sql"
printf '%s\n' '-- MOCK_MIGRATION_NAME=048_mock_new.sql' >> "$APP_DIR/docs/migraciones/048_mock_new.sql"
before_sql=$(count_lines '^SQL|048_mock_new.sql$' "$LOG_FILE")
run_deploy "$STATE_FILE" env
[ "$(count_lines '^SQL|048_mock_new.sql$' "$LOG_FILE")" -eq $((before_sql + 1)) ] || fail 'new migration did not execute once'
grep -Fqx 'M|048_mock_new.sql|'"$(sha256sum "$APP_DIR/docs/migraciones/048_mock_new.sql" | awk '{print $1}')" "$STATE_FILE" || fail 'new migration was not recorded'
run_deploy "$STATE_FILE" env
[ "$(count_lines '^SQL|048_mock_new.sql$' "$LOG_FILE")" -eq $((before_sql + 1)) ] || fail 'recorded new migration was replayed'

cp "$APP_DIR/docs/migraciones/047_discogs_manual_batches.sql" "$APP_DIR/docs/migraciones/049_mock_failure.sql"
printf '%s\n' '-- MOCK_MIGRATION_NAME=049_mock_failure.sql' >> "$APP_DIR/docs/migraciones/049_mock_failure.sql"
cp "$APP_DIR/docs/migraciones/047_discogs_manual_batches.sql" "$APP_DIR/docs/migraciones/050_mock_later.sql"
printf '%s\n' '-- MOCK_MIGRATION_NAME=050_mock_later.sql' >> "$APP_DIR/docs/migraciones/050_mock_later.sql"
before_compose=$(count_lines '^COMPOSE|' "$LOG_FILE")
if MOCK_FAIL_SQL=1 run_deploy "$STATE_FILE" env; then
    fail 'failed migration returned success'
fi
[ "$(count_lines '^SQL|049_mock_failure.sql$' "$LOG_FILE")" -eq 1 ] || fail 'failed migration was not attempted'
[ "$(count_lines '^SQL|050_mock_later.sql$' "$LOG_FILE")" -eq 0 ] || fail 'later migration executed after failure'
[ "$(count_lines '^COMPOSE|' "$LOG_FILE")" -eq "$before_compose" ] || fail 'services were replaced after migration failure'
! grep -Fq 'M|049_mock_failure.sql|' "$STATE_FILE" || fail 'failed migration was recorded'

printf '\n-- checksum drift --\n' >> "$APP_DIR/docs/migraciones/001_cantidad_copias.sql"
before_compose=$(count_lines '^COMPOSE|' "$LOG_FILE")
if run_deploy "$STATE_FILE" env; then
    fail 'checksum drift returned success'
fi
[ "$(count_lines '^COMPOSE|' "$LOG_FILE")" -eq "$before_compose" ] || fail 'services were replaced after checksum drift'
[ "$(count_lines '^SQL|001_cantidad_copias.sql$' "$LOG_FILE")" -eq 0 ] || fail 'drifted migration was executed'

if run_deploy "$MISSING_STATE" env; then
    fail 'missing ledger returned success'
fi
if run_deploy "$EMPTY_BASELINE_STATE" env; then
    fail 'missing baseline returned success'
fi
printf '%s\n' 'Migration tracking mock scenarios passed: baseline, repeat, skip, new migration, SQL failure, checksum drift, and missing ledger.'
