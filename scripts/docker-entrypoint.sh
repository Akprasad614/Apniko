#!/bin/sh
set -eu

read_secret() {
  secret_file="$1"
  variable_name="$2"
  if [ ! -r "$secret_file" ]; then
    echo "Required secret file is not readable: $secret_file" >&2
    exit 1
  fi
  secret_value="$(cat "$secret_file")"
  if [ -z "$secret_value" ]; then
    echo "Required secret file is empty: $secret_file" >&2
    exit 1
  fi
  export "$variable_name=$secret_value"
  unset secret_value
}

: "${DB_PASSWORD_FILE:=/run/secrets/db_password}"
: "${JWT_SECRET_FILE:=/run/secrets/jwt_secret}"
read_secret "$DB_PASSWORD_FILE" DB_PASSWORD
read_secret "$JWT_SECRET_FILE" JWT_SECRET

: "${SPRING_PROFILES_ACTIVE:=prod}"
export SPRING_PROFILES_ACTIVE

exec java -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -jar /app/apniko.jar
