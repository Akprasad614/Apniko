#!/usr/bin/env bash
set -euo pipefail

# This script only creates/populates localcart_test. It never drops or writes
# to the source localcart database.
SOURCE_DB="localcart"
TARGET_DB="localcart_test"

if ! command -v mysql >/dev/null 2>&1 || ! command -v mysqldump >/dev/null 2>&1; then
  echo "mysql and mysqldump must be available on PATH." >&2
  exit 1
fi

read -r -s -p "Enter your local MySQL root password: " MYSQL_ROOT_PASSWORD
printf '\n'
if [[ -z "${MYSQL_ROOT_PASSWORD}" ]]; then
  echo "A MySQL password is required." >&2
  exit 1
fi

export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"
unset MYSQL_ROOT_PASSWORD
trap 'unset MYSQL_PWD' EXIT

SOURCE_TABLE_COUNT="$(mysql --protocol=TCP -h localhost -u root -N -s \
  -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${SOURCE_DB}';")"
if [[ ! "${SOURCE_TABLE_COUNT}" =~ ^[0-9]+$ ]] || [[ "${SOURCE_TABLE_COUNT}" == "0" ]]; then
  echo "Source database ${SOURCE_DB} does not exist or contains no tables." >&2
  exit 1
fi

# Create target only when absent. Never drop or overwrite an existing test database.
mysql --protocol=TCP -h localhost -u root \
  -e "CREATE DATABASE IF NOT EXISTS \`${TARGET_DB}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

TARGET_TABLE_COUNT="$(mysql --protocol=TCP -h localhost -u root -N -s \
  -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${TARGET_DB}';")"
if [[ "${TARGET_TABLE_COUNT}" != "0" ]]; then
  echo "Refusing to overwrite ${TARGET_DB}: it already contains ${TARGET_TABLE_COUNT} tables." >&2
  echo "Back up and refresh only ${TARGET_DB} manually before rerunning this script." >&2
  exit 2
fi

echo "Cloning ${SOURCE_DB} into the empty ${TARGET_DB} database..."
mysqldump --protocol=TCP -h localhost -u root \
  --set-gtid-purged=OFF --single-transaction --routines --triggers --no-tablespaces \
  "${SOURCE_DB}" | mysql --protocol=TCP -h localhost -u root "${TARGET_DB}"

unset MYSQL_PWD
trap - EXIT
echo "Database clone completed: ${TARGET_DB}"
echo "Your original ${SOURCE_DB} database was not modified."
