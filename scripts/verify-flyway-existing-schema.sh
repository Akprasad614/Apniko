#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

: "${DB_PASSWORD:?DB_PASSWORD must be supplied by the disposable CI MySQL service}"
: "${JWT_SECRET:?JWT_SECRET must be supplied by the backend CI job}"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="root"
TEST_SCHEMA="apniko_legacy_upgrade_ci"
MIGRATION_URL="jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${TEST_SCHEMA}?allowPublicKeyRetrieval=true&useSSL=false"

mysql_ci() {
  MYSQL_PWD="$DB_PASSWORD" mysql --protocol=tcp --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USER" "$@"
}

# This schema is created only inside the disposable CI MySQL service.
mysql_ci -e "CREATE DATABASE `$TEST_SCHEMA` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
mysql_ci "$TEST_SCHEMA" < src/main/resources/db/migration/V1__legacy_localcart_schema_baseline.sql

export FLYWAY_URL="$MIGRATION_URL"
export FLYWAY_USER="$MYSQL_USER"
export FLYWAY_PASSWORD="$DB_PASSWORD"

# The schema already contains the V1 shape, so baseline it at 1 and apply only V2.
./mvnw -B -ntp flyway:baseline -Dflyway.baselineVersion=1
./mvnw -B -ntp flyway:migrate
./mvnw -B -ntp flyway:info

unset FLYWAY_PASSWORD

table_count="$(mysql_ci --batch --skip-column-names "$TEST_SCHEMA" -e "
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = '$TEST_SCHEMA'
    AND table_type = 'BASE TABLE'
    AND table_name <> 'flyway_schema_history'
")"
[[ "$table_count" == "23" ]] || {
  echo "Expected 23 application tables after V2; found $table_count." >&2
  exit 1
}

# Start Hibernate in production validation mode and exercise a real JPA write/read
# against the upgraded legacy schema. Flyway stays disabled in this app process.
SPRING_PROFILES_ACTIVE=prod \
DB_URL="$MIGRATION_URL" \
DB_USERNAME="$MYSQL_USER" \
DB_PASSWORD="$DB_PASSWORD" \
JWT_SECRET="$JWT_SECRET" \
./mvnw -B -ntp -Dtest=OrderPersistenceIntegrationTest test

echo "Existing-schema Flyway baseline, V2 upgrade and Hibernate validation passed."
