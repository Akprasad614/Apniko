#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -z "${DB_PASSWORD:-}" ]]; then
  read -r -s -p "Enter the MySQL password configured as DB_PASSWORD: " DB_PASSWORD
  printf '\n'
fi
if [[ -z "${DB_PASSWORD:-}" ]]; then
  echo "DB_PASSWORD is required." >&2
  exit 1
fi

if [[ -z "${JWT_SECRET:-}" ]]; then
  read -r -s -p "Enter your existing LocalCart JWT secret: " JWT_SECRET
  printf '\n'
fi
if [[ -z "${JWT_SECRET:-}" ]]; then
  echo "JWT_SECRET is required." >&2
  exit 1
fi

# Fail early rather than accidentally starting against the main localcart database.
TEST_DB_TABLE_COUNT="$(MYSQL_PWD="${DB_PASSWORD}" mysql --protocol=TCP -h localhost -P 3306 -u root -N -s \
  -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='localcart_test';")"
if [[ ! "${TEST_DB_TABLE_COUNT}" =~ ^[0-9]+$ ]] || [[ "${TEST_DB_TABLE_COUNT}" == "0" ]]; then
  echo "Database localcart_test is missing or empty. Run scripts/clone-localcart-test-db.sh first." >&2
  exit 1
fi

export DB_PASSWORD
export JWT_SECRET
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/localcart_test?allowPublicKeyRetrieval=true&useSSL=false"
export SERVER_PORT=8081

echo "Starting LocalCart against localcart_test on port 8081..."
./mvnw spring-boot:run
