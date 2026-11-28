#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-}"
if [[ -z "$BASE_URL" ]]; then
  echo "Set BASE_URL to the staging HTTPS origin, e.g. https://staging.example.com" >&2
  exit 2
fi
case "$BASE_URL" in https://*) ;; *) echo "Refusing non-HTTPS staging URL." >&2; exit 2 ;; esac
case "$BASE_URL" in *localhost*|*127.0.0.1*) echo "Use a real staging hostname." >&2; exit 2 ;; esac
if [[ -n "${AUTH_TOKEN:-}" ]]; then
  echo "Do not pass bearer credentials on the command line. Use a private Postman environment for authenticated checks." >&2
  exit 2
fi
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
check() {
  local name="$1" url="$2" expected="$3" code
  code="$(curl --silent --show-error --output "$tmp" --write-out '%{http_code}' --max-time 15 "$url")"
  if [[ "$code" != "$expected" ]]; then
    echo "FAIL $name: expected HTTP $expected, got $code" >&2
    exit 1
  fi
  echo "PASS $name (HTTP $code)"
}
check "HTTPS frontend" "$BASE_URL/" "200"
check "public actuator blocked" "$BASE_URL/actuator/health" "404"
echo "Public smoke checks passed. Complete authenticated journeys using the private Postman environment and PHASE14-STAGING-READINESS.md."
