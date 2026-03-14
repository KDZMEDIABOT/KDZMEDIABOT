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

DOCKER_BUILDKIT=0 docker compose -f "${COMPOSE_FILE}" --env-file "${PROD_ENV_FILE}" logs -f frontend auth_sidecar readingplus_mcp_sidecar redis
exit 1
