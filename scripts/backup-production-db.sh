#!/usr/bin/env bash
set -euo pipefail
umask 077

required=(DB_HOST DB_NAME DB_USERNAME DB_PASSWORD_FILE)
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo "Required environment variable is missing: $name" >&2
    exit 2
  fi
done

if [[ ! -r "$DB_PASSWORD_FILE" || ! -s "$DB_PASSWORD_FILE" ]]; then
  echo "DB_PASSWORD_FILE must point to a non-empty readable secret file." >&2
  exit 2
fi

command -v mysqldump >/dev/null || { echo "mysqldump is required." >&2; exit 2; }
command -v gzip >/dev/null || { echo "gzip is required." >&2; exit 2; }
command -v sha256sum >/dev/null || { echo "sha256sum is required." >&2; exit 2; }

DB_PORT="${DB_PORT:-3306}"
DB_SSL_MODE="${DB_SSL_MODE:-VERIFY_IDENTITY}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

MYSQL_PWD="$(cat "$DB_PASSWORD_FILE")"
export MYSQL_PWD
trap 'unset MYSQL_PWD' EXIT

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
output="$BACKUP_DIR/${DB_NAME}-${timestamp}.sql.gz"
temporary="$output.tmp.$$"
trap 'rm -f "$temporary"; unset MYSQL_PWD' EXIT

mysqldump \
  --host="$DB_HOST" \
  --port="$DB_PORT" \
  --user="$DB_USERNAME" \
  --ssl-mode="$DB_SSL_MODE" \
  ${DB_SSL_CA_FILE:+--ssl-ca="$DB_SSL_CA_FILE"} \
  --single-transaction \
  --routines \
  --triggers \
  --events \
  --hex-blob \
  --set-gtid-purged=OFF \
  "$DB_NAME" | gzip -9 > "$temporary"

gzip -t "$temporary"
mv "$temporary" "$output"
chmod 600 "$output"
(cd "$BACKUP_DIR" && sha256sum "$(basename "$output")" > "$(basename "$output").sha256")
chmod 600 "$output.sha256"

echo "Database backup created: $output"
echo "Verify the checksum and copy the encrypted backup to a separate storage account."
