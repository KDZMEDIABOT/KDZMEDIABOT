#!/usr/bin/env bash
# redeploy_backend_prod.sh
# Compose-based backend + sidecar redeploy for prod profile.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}/.."
COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.prod.yml"
PROD_ENV_FILE="${PROJECT_ROOT}/.env.prod"

if [ ! -f "${PROD_ENV_FILE}" ]; then
  echo "Missing required env file: ${PROD_ENV_FILE}"
  echo "Create it from .env.prod.template first."
  exit 1
fi

set -a
. "${PROD_ENV_FILE}"
set +a

HTTPD_SERVER_PORT="${HTTPD_SERVER_PORT:-8443}"
AUTH_PORT="${AUTH_SERVICE_HTTPD_PORT:-3002}"
BACKEND_URL="http://localhost:${HTTPD_SERVER_PORT}/api/workflow/health"
AUTH_URL="http://localhost:${AUTH_PORT}/health"

echo "=============================================="
echo "Redeploying Backend (PROD, docker compose)"
echo "=============================================="

OPT_LOG="--progress=plain"
#--driver-opt env.BUILDKIT_STEP_LOG_MAX_SIZE=1000000000 --progress=plain"
DOCKER_BUILDKIT=0 docker compose $OPT_LOG -f "${COMPOSE_FILE}" --env-file "${PROD_ENV_FILE}" up -d --build redis postgres backend auth_sidecar readingplus_mcp_sidecar
DOCKER_BUILDKIT=0 docker compose $OPT_LOG -f "${COMPOSE_FILE}" --env-file "${PROD_ENV_FILE}" restart redis backend readingplus_mcp_sidecar

echo "Waiting for backend and sidecar health..."
for i in $(seq 1 180); do
  if curl -fsS "${BACKEND_URL}" >/dev/null 2>&1 && curl -fsS "${AUTH_URL}" >/dev/null 2>&1; then
    echo "Backend and auth sidecar are ready."
    echo "Backend URL: ${BACKEND_URL}"
    echo "Auth URL: ${AUTH_URL}"
    exit 0
  fi
  sleep 1
done

echo "Timeout waiting for backend/auth readiness."
DOCKER_BUILDKIT=0 docker compose $OPT_LOG -f "${COMPOSE_FILE}" --env-file "${PROD_ENV_FILE}" logs --tail=200 backend auth_sidecar readingplus_mcp_sidecar
exit 1
