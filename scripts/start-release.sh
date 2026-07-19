#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${1:-$ROOT_DIR/.env.release.local}"
MAVEN_REPO_LOCAL="${MAVEN_REPO_LOCAL:-$ROOT_DIR/.m2-temp}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file: $ENV_FILE"
  echo "Copy $ROOT_DIR/.env.release.example to $ROOT_DIR/.env.release.local and fill in the real values first."
  exit 1
fi

set -a
source "$ENV_FILE"
set +a

export SPRING_PROFILES_ACTIVE=release

DATABASE_ENABLED="${GAOKAO_DATABASE_ENABLED:-${GAOKAO_POSTGRES_ENABLED:-${GAOKAO_MYSQL_ENABLED:-false}}}"
DATABASE_URL="${GAOKAO_DATABASE_URL:-${GAOKAO_POSTGRES_URL:-${GAOKAO_MYSQL_URL:-}}}"
DATABASE_KIND="state-file"
if [[ "$DATABASE_ENABLED" == "true" ]]; then
  DATABASE_KIND="database"
  if [[ "$DATABASE_URL" == *postgres* ]]; then
    DATABASE_KIND="postgres"
  elif [[ "$DATABASE_URL" == *mysql* ]]; then
    DATABASE_KIND="mysql"
  fi
fi

echo "Starting gaokao-essay-backend in release mode..."
echo "Env file: $ENV_FILE"
echo "Database enabled: ${DATABASE_ENABLED}"
echo "Database kind: ${DATABASE_KIND}"
echo "AI provider: ${GAOKAO_AI_PROVIDER:-unset}"
echo "OCR enabled: ${GAOKAO_OCR_ENABLED:-false}"
echo "Payment enabled: ${GAOKAO_PAYMENT_ENABLED:-false}"

exec "$ROOT_DIR/mvnw" -q -Dmaven.repo.local="$MAVEN_REPO_LOCAL" spring-boot:run
