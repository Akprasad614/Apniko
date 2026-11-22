#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

DB_NAME="${DB_NAME:-localcart}"
MYSQL_USER="${MYSQL_USER:-root}"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="$HOME/Desktop/apniko-catalogue-reset-$STAMP"
BACKUP_FILE="$BACKUP_DIR/$DB_NAME-before-reset.sql"

if ! command -v mysql >/dev/null 2>&1 || ! command -v mysqldump >/dev/null 2>&1; then
  echo "mysql and mysqldump must be installed and available on PATH." >&2
  exit 1
fi

echo "This will clear the local Apniko product catalogue, product images/candidates,"
echo "source provenance, categories, shop inventory, carts, order lines and local"
echo "order/payment history. Users, roles, admin accounts, shops and customer"
echo "addresses will be preserved."
echo
echo "A full database backup will be saved to:"
echo "  $BACKUP_FILE"
echo
read -r -p 'Type RESET CATALOGUE to continue: ' CONFIRM
if [[ "$CONFIRM" != "RESET CATALOGUE" ]]; then
  echo "Reset cancelled. No database changes were made."
  exit 0
fi

mkdir -p "$BACKUP_DIR"
echo "Creating full database backup..."
mysqldump --single-transaction --routines --triggers -u "$MYSQL_USER" -p "$DB_NAME" > "$BACKUP_FILE"
test -s "$BACKUP_FILE"

# Preserve any locally supplied BigBasket CSV. The reset touches database rows only.

echo "Clearing local catalogue and product-linked development data..."
mysql -u "$MYSQL_USER" -p "$DB_NAME" < scripts/catalogue/reset-local-catalogue.sql

echo
echo "Catalogue reset completed. Backup retained at:"
echo "  $BACKUP_DIR"
