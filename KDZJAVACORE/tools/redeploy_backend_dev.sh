#!/usr/bin/env bash
# redeploy_backend_dev.sh
# Compose-based backend + sidecar redeploy for dev profile.

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

HTTPD_SERVER_PORT="${HTTPD_SERVER_PORT:-8080}"
AUTH_PORT="${AUTH_SERVICE_HTTPD_PORT:-3001}"
BACKEND_URL="http://localhost:${HTTPD_SERVER_PORT}/api/workflow/health"
AUTH_URL="http://localhost:${AUTH_PORT}/health"

echo "=============================================="
echo "Redeploying Backend (DEV, docker compose)"
echo "=============================================="

DOCKER_BUILDKIT=1 COMPOSE_BAKE=true docker compose -f "${COMPOSE_FILE}" --env-file "${DEV_ENV_FILE}" build backend
DOCKER_BUILDKIT=1 COMPOSE_BAKE=true docker compose -f "${COMPOSE_FILE}" --env-file "${DEV_ENV_FILE}" build auth_sidecar
DOCKER_BUILDKIT=1 COMPOSE_BAKE=true docker compose -f "${COMPOSE_FILE}" --env-file "${DEV_ENV_FILE}" build readingplus_mcp_sidecar
DOCKER_BUILDKIT=1 COMPOSE_BAKE=true docker compose -f "${COMPOSE_FILE}" --env-file "${DEV_ENV_FILE}" up -d redis postgres backend auth_sidecar readingplus_mcp_sidecar
docker compose -f "${COMPOSE_FILE}" --env-file "${DEV_ENV_FILE}" restart redis backend readingplus_mcp_sidecar

echo "Waiting for backend and sidecar health..."
for i in $(seq 1 120); do
  backend_ok=0
  auth_ok=0
  if curl -fsS "${BACKEND_URL}" >/dev/null 2>&1; then
    backend_ok=1
  fi
  echo -n "."
  if curl -fsS "${AUTH_URL}" >/dev/null 2>&1; then
    auth_ok=1
  fi
  echo -n "."

  if [ "${backend_ok}" -eq 1 ] && [ "${auth_ok}" -eq 1 ]; then
    echo
    echo "Backend and auth sidecar are ready."
    echo "Backend URL: ${BACKEND_URL}"
    echo "Auth URL: ${AUTH_URL}"
    exit 0
  fi
  sleep 1
done

echo
echo "Timeout waiting for backend/auth readiness."
docker compose -f "${COMPOSE_FILE}" --env-file "${DEV_ENV_FILE}" logs --tail=200 backend auth_sidecar readingplus_mcp_sidecar
exit 1
