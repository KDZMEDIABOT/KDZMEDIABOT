#!/usr/bin/env bash
# test_backend_spawn_all_necessary_subshells.sh
# Backend-focused tests with compose-managed lifecycle.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}/.."
DEV_ENV_FILE="${PROJECT_ROOT}/.env.dev"
COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.dev.yml"

if [ ! -f "${DEV_ENV_FILE}" ]; then
  echo "Missing required env file: ${DEV_ENV_FILE}"
  exit 1
fi

# Prevent parallel runs of this script.
SELF_PID="$$"
SELF_NAME="$(basename "${BASH_SOURCE[0]}")"
for pid in $(pgrep -f "${SELF_NAME}" || true); do
  if [ "${pid}" != "${SELF_PID}" ]; then
    echo "Another ${SELF_NAME} instance is running (pid=${pid}). Exiting."
    exit 1
  fi
done

set -a
. "${DEV_ENV_FILE}"
set +a

echo "----------------------------------------------------------------------"
echo "Compose Lifecycle (backend + sidecar + db)"
echo "----------------------------------------------------------------------"
docker compose -f "${COMPOSE_FILE}" --env-file "${DEV_ENV_FILE}" up -d --build postgres backend auth_sidecar

BACKEND_URL="http://localhost:${HTTPD_SERVER_PORT:-8080}/api/workflow/health"
AUTH_URL="http://localhost:${AUTH_SERVICE_HTTPD_PORT:-3001}/health"
for i in $(seq 1 120); do
  if curl -fsS "${BACKEND_URL}" >/dev/null 2>&1 && curl -fsS "${AUTH_URL}" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo "----------------------------------------------------------------------"
echo "Backend Tests"
echo "----------------------------------------------------------------------"

echo "[BACKEND-1/3] auth_service tests..."
( cd "${PROJECT_ROOT}/auth_service" && npm install && npm test )

echo "[BACKEND-2/3] generic_backend tests..."
( cd "${PROJECT_ROOT}/generic_backend" && mvn clean test -Dspring.profiles.active=dev )

echo "[BACKEND-3/3] customer_project tests..."
( cd "${PROJECT_ROOT}/customer_project" && mvn test -Dspring.profiles.active=dev )

echo "----------------------------------------------------------------------"
echo "Backend Tests: ALL PASSED"
