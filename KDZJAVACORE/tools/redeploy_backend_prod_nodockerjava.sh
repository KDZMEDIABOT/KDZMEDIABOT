#!/usr/bin/env bash
# redeploy_backend_prod_nodockerjava.sh
# Deploy JVM outside Docker, keep infra (PostgreSQL, Redis, auth_sidecar) in Docker containers

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

# Load environment variables
set -a
. "${PROD_ENV_FILE}"
set +a

HTTPD_SERVER_PORT="${HTTPD_SERVER_PORT:-8443}"
AUTH_PORT="${AUTH_SERVICE_HTTPD_PORT:-3002}"

# Build JARs first
echo "=============================================="
echo "Building JARs (host Maven, no Docker)"
echo "=============================================="

rm -rf "${PROJECT_ROOT}/generic_backend/target" "${PROJECT_ROOT}/customer_project/target"
# Build generic_backend as library (no Spring Boot fat JAR)
echo "Building generic_backend..."
mvn -f "${PROJECT_ROOT}/generic_backend/pom.xml" \
    -Dspring.boot.repackage.skip=true \
    clean install -DskipTests

echo "Building customer_project..."
mvn -f "${PROJECT_ROOT}/customer_project/pom.xml" \
    package -DskipTests

echo "JARs built successfully."

# Start only Docker infra (no backend JVM)
echo ""
echo "=============================================="
echo "Starting Docker infra (PostgreSQL, Redis, auth_sidecar)"
echo "=============================================="

# Get host IP for auth_sidecar to connect to host JVM
HOST_IP="${HOST_IP:-host.docker.internal}"

# Start only postgres, redis, auth_sidecar, readingplus_mcp_sidecar - NOT the backend
docker compose -f "${COMPOSE_FILE}" \
    --env-file "${PROD_ENV_FILE}" \
    up -d --remove-orphans \
    postgres redis #auth_sidecar readingplus_mcp_sidecar

# Network setup: backend is outside Docker, so auth_sidecar needs to reach host
# Pause briefly to let containers start
sleep 2

echo ""
echo "=============================================="
echo "Starting JVM on host (outside Docker)"
echo "=============================================="

# Find the built JAR
JAR_FILE=$(ls "${PROJECT_ROOT}/customer_project/target/"aisystem-*.jar 2>/dev/null | head -1)

if [ -z "${JAR_FILE}" ] || [ ! -f "${JAR_FILE}" ]; then
    echo "ERROR: Could not find built JAR in customer_project/target/"
    exit 1
fi

echo "Starting JVM with JAR: ${JAR_FILE}"
echo "Port: ${HTTPD_SERVER_PORT}"

# Run JVM on host with prod profile
# Connects to postgres on localhost (mapped port)
SPRING_PROFILES_ACTIVE=prod \
SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:15432/${DB_NAME}" \
SPRING_DATASOURCE_USERNAME="${DB_USERNAME}" \
SPRING_DATASOURCE_PASSWORD="${DB_PASSWORD}" \
SPRING_FLYWAY_LOCATIONS="db/migration" \
java -jar "${JAR_FILE}" &

# Save PID for potential later kill
JVM_PID=$!
echo "JVM started with PID: ${JVM_PID}"
echo "${JVM_PID}" > /tmp/aisystem-backend-prod.pid

# Wait for JVM to be ready
echo ""
echo "Waiting for backend to start..."
BACKEND_URL="http://localhost:${HTTPD_SERVER_PORT}/api/workflow/health"
AUTH_URL="http://localhost:${AUTH_PORT}/health"

for i in $(seq 1 180); do
    if curl -fsS "${BACKEND_URL}" >/dev/null 2>&1 && curl -fsS "${AUTH_URL}" >/dev/null 2>&1; then
        echo ""
        echo "=============================================="
        echo "Backend and auth sidecar are ready."
        echo "Backend URL: ${BACKEND_URL}"
        echo "Auth URL: ${AUTH_URL}"
        echo "=============================================="
        exit 0
    fi
    echo -n "."
    sleep 1
done

echo ""
echo "Timeout waiting for backend/auth readiness."
exit 1
