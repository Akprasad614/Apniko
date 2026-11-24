#!/usr/bin/env bash
set -euo pipefail
umask 077

required=(DB_HOST DB_NAME DB_USERNAME DB_PASSWORD_FILE BACKUP_FILE CONFIRM_RESTORE CONFIRM_DATABASE_NAME)
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo "Required environment variable is missing: $name" >&2
    exit 2
  fi
done

if [[ "$CONFIRM_RESTORE" != "YES" ]]; then
  echo "Restore refused. Set CONFIRM_RESTORE=YES after the target has been reviewed." >&2
  exit 2
fi
if [[ "$CONFIRM_DATABASE_NAME" != "$DB_NAME" ]]; then
  echo "Restore refused: CONFIRM_DATABASE_NAME must exactly match DB_NAME." >&2
  exit 2
fi
case "$DB_NAME" in
  mysql|sys|information_schema|performance_schema|"")
    echo "Restore refused: system or empty database name." >&2
    exit 2
    ;;
esac
if [[ ! -r "$DB_PASSWORD_FILE" || ! -s "$DB_PASSWORD_FILE" ]]; then
  echo "DB_PASSWORD_FILE must point to a non-empty readable secret file." >&2
  exit 2
fi
if [[ ! -r "$BACKUP_FILE" || ! -s "$BACKUP_FILE" ]]; then
  echo "BACKUP_FILE must point to a non-empty readable backup." >&2
  exit 2
fi
command -v mysql >/dev/null || { echo "mysql client is required." >&2; exit 2; }
command -v gzip >/dev/null || { echo "gzip is required." >&2; exit 2; }

gzip -t "$BACKUP_FILE"
if [[ -f "$BACKUP_FILE.sha256" ]]; then
  (cd "$(dirname "$BACKUP_FILE")" && sha256sum --check "$(basename "$BACKUP_FILE").sha256")
fi

DB_PORT="${DB_PORT:-3306}"
DB_SSL_MODE="${DB_SSL_MODE:-VERIFY_IDENTITY}"
MYSQL_PWD="$(cat "$DB_PASSWORD_FILE")"
export MYSQL_PWD
trap 'unset MYSQL_PWD' EXIT

echo "Restoring $BACKUP_FILE into database $DB_NAME on $DB_HOST:$DB_PORT."
echo "This overwrites tables in the selected database. Ensure a recent pre-restore backup exists."
gzip -dc "$BACKUP_FILE" | mysql \
  --host="$DB_HOST" \
  --port="$DB_PORT" \
  --user="$DB_USERNAME" \
  --ssl-mode="$DB_SSL_MODE" \
  ${DB_SSL_CA_FILE:+--ssl-ca="$DB_SSL_CA_FILE"} \
  "$DB_NAME"

echo "Restore command completed. Run application smoke tests before directing traffic to this database."
