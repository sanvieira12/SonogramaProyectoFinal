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
run_deploy "$STATE_FILE" env
[ "$(count_lines '^SQL|' "$LOG_FILE")" -eq 0 ] || fail 'already-applied migrations were replayed'
[ "$(count_lines '^COMPOSE|' "$LOG_FILE")" -gt "$before_compose" ] || fail 'successful deployment did not reach continuation'

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
