#!/usr/bin/env bash
# redeploy_frontend_dev.sh
# Compose-based frontend redeploy for dev profile.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}/.."
COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.dev.yml"
DEV_ENV_FILE="${PROJECT_ROOT}/.env.dev"

if [ ! -f "${DEV_ENV_FILE}" ]; then
  echo "Missing required env file: ${DEV_ENV_FILE}"
  echo "Create it from .env.dev.template first."
  exit 1
fi

set -a
. "${DEV_ENV_FILE}"
set +a

FRONTEND_PORT="${FRONTEND_HTTPD_PORT:-9000}"
FRONTEND_URL="http://127.0.0.1:${FRONTEND_PORT}"

echo "=============================================="
echo "Redeploying Frontend (DEV, docker compose)"
echo "=============================================="

docker compose -f "${COMPOSE_FILE}" --env-file "${DEV_ENV_FILE}" up -d --build --no-deps frontend

echo "Waiting for frontend health..."
for i in $(seq 1 120); do
  if curl -fsS "${FRONTEND_URL}" >/dev/null 2>&1; then
    echo "Frontend is ready at ${FRONTEND_URL}"
    exit 0
  fi
  sleep 1
done

echo "Timeout waiting for frontend readiness."
docker compose -f "${COMPOSE_FILE}" --env-file "${DEV_ENV_FILE}" logs --tail=200 frontend
exit 1
