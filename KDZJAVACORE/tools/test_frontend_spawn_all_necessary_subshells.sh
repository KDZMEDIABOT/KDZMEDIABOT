#!/usr/bin/env bash
# test_frontend_spawn_all_necessary_subshells.sh
# Frontend-focused tests with compose-managed lifecycle.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}/.."
FRONTEND_DIR="${PROJECT_ROOT}/frontend"
DEV_ENV_FILE="${PROJECT_ROOT}/.env.dev"
TESTS_ENV_FILE="${PROJECT_ROOT}/.env.tests"
COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.dev.yml"

if [ ! -f "${DEV_ENV_FILE}" ]; then
  echo "Missing required env file: ${DEV_ENV_FILE}"
  exit 1
fi

set -a
. "${DEV_ENV_FILE}"
set +a

ENABLE_PLAYWRIGHT_TESTS="${ENABLE_PLAYWRIGHT_TESTS:-true}"
if [ -f "${TESTS_ENV_FILE}" ]; then
  set -a
  . "${TESTS_ENV_FILE}"
  set +a
fi
ENABLE_PLAYWRIGHT_TESTS="$(printf '%s' "${ENABLE_PLAYWRIGHT_TESTS}" | tr '[:upper:]' '[:lower:]')"

echo "----------------------------------------------------------------------"
echo "Compose Lifecycle (frontend + backend + sidecar + db)"
echo "----------------------------------------------------------------------"
docker compose -f "${COMPOSE_FILE}" --env-file "${DEV_ENV_FILE}" up -d --build postgres backend auth_sidecar frontend

FE_URL="http://127.0.0.1:${FRONTEND_HTTPD_PORT:-9000}"
for i in $(seq 1 120); do
  if curl -fsS "${FE_URL}" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo "----------------------------------------------------------------------"
echo "Frontend Tests"
echo "----------------------------------------------------------------------"

echo "[FRONTEND-1/4] Installing frontend dependencies..."
( cd "${FRONTEND_DIR}" && npm install )

echo "[FRONTEND-2/4] Running frontend unit tests..."
( cd "${FRONTEND_DIR}" && npm run test:unit -- --run )

if [ "${ENABLE_PLAYWRIGHT_TESTS}" = "true" ]; then
  echo "[FRONTEND-3/4] Running Playwright E2E tests..."
  ( cd "${FRONTEND_DIR}" && PLAYWRIGHT_BASE_URL="${FE_URL}" npx playwright test --reporter=line --config=playwright.config.ts )
else
  echo "[FRONTEND-3/4] Skipping Playwright E2E tests (ENABLE_PLAYWRIGHT_TESTS=false)"
fi

echo "[FRONTEND-4/4] Running TypeScript compilation check..."
( cd "${FRONTEND_DIR}" && npx tsc --noEmit )

echo "----------------------------------------------------------------------"
echo "Frontend Tests: ALL PASSED"
