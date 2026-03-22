#!/usr/bin/env bash
# redeploy_backend_prod_nodockerjava.sh
# Deploy JVM outside Docker, keep infra (PostgreSQL, Redis, auth_sidecar) in Docker containers

SCRIPT_DIR="$(cd $(dirname ${BASH_SOURCE[0]}) && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}/.."

${PROJECT_ROOT}/tools/kill_backend_prod_nodockerjava.sh

set -euo pipefail

COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.prod.yml"
PROD_ENV_FILE="${PROJECT_ROOT}/.env.prod"

if [ ! -f ${PROD_ENV_FILE} ]; then
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
    clean package -DskipTests
#echo "JAR built successfully."

# Start only Docker infra (no backend JVM)
echo ""
echo "=============================================="
echo "Restarting Docker infrastructure (PostgreSQL, Redis, auth_sidecar)"
echo "=============================================="

# Get host IP for auth_sidecar to connect to host JVM
HOST_IP="${HOST_IP:-host.docker.internal}"

# Start only postgres, redis, auth_sidecar, readingplus_mcp_sidecar - NOT the backend
docker compose -f "${COMPOSE_FILE}" \
    --env-file "${PROD_ENV_FILE}" \
    down --remove-orphans \
    postgres redis auth_sidecar readingplus_mcp_sidecar frontend
docker compose -f "${COMPOSE_FILE}" \
    --env-file "${PROD_ENV_FILE}" \
    up -d --build --remove-orphans \
    postgres redis auth_sidecar readingplus_mcp_sidecar frontend

# Network setup: backend is outside Docker, so auth_sidecar needs to reach host
# Pause briefly to let containers start
sleep 2

echo ""
echo "=============================================="
echo "Starting JVM on host (outside Docker)"
echo "=============================================="

# Find the built JAR
JAR_FILE=$(ls -1 ${PROJECT_ROOT}/customer_project/target/aisystem-*.jar 2>/dev/null | head -1)

echo "Starting JVM with JAR: ${JAR_FILE}"

sudo chown user:user ${JAR_FILE}

if [ -z "${JAR_FILE}" ] || [ ! -f "${JAR_FILE}" ]; then
    echo "ERROR: Could not find built JAR in customer_project/target/"
    exit 1
fi

echo "Web UI Port: ${HTTPD_SERVER_PORT}"

# Run JVM on host with prod profile
# Connects to postgres on localhost (mapped port)
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:15432/${DB_NAME}"
export SPRING_DATASOURCE_USERNAME="${DB_USERNAME}"
export SPRING_DATASOURCE_PASSWORD="${DB_PASSWORD}"
export SPRING_FLYWAY_LOCATIONS="db/migration"

echo export SPRING_PROFILES_ACTIVE=prod
echo export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:15432/${DB_NAME}"
echo export SPRING_DATASOURCE_USERNAME="${DB_USERNAME}"
echo export SPRING_DATASOURCE_PASSWORD="${DB_PASSWORD}"
echo export SPRING_FLYWAY_LOCATIONS="db/migration"

echo "SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE} sudo java -jar ${JAR_FILE} >/var/log/kdzbot_java_srv.log 2>&1 &"

# Start the JVM with sudo, capturing the Java PID
JVM_PID=$(sudo bash -c 'JAR_FILE="'"$JAR_FILE"'"
SPRING_PROFILES_ACTIVE=prod \
SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:15432/'"$DB_NAME"'" \
SPRING_DATASOURCE_USERNAME="'"$DB_USERNAME"'" \
SPRING_DATASOURCE_PASSWORD="'"$DB_PASSWORD"'" \
SPRING_FLYWAY_LOCATIONS=db/migration \
java -jar "$JAR_FILE" >/var/log/kdzbot_java_srv.log 2>&1 &
echo $!')

echo KDZBOT JVM log: /var/log/kdzbot_java_srv.log

# Save PID for potential later kill
echo "JVM started with PID: ${JVM_PID}"
echo "${JVM_PID}" | sudo tee /tmp/kdzbot-backend-prod.pid >/dev/null

#echo tail:
#tail -f /var/log/kdzbot_java_srv.log
#
#exit 0

# Wait for JVM to be ready
#echo ""
echo "Waiting for backend to start..."
BACKEND_URL="http://localhost:${HTTPD_SERVER_PORT}/api/health"
AUTH_URL="http://localhost:${AUTH_PORT}/health"

for i in $(seq 1 180); do
    if curl -fsS "${BACKEND_URL}" >/dev/null 2>&1 && curl -fsS "${AUTH_URL}" >/dev/null 2>&1; then
        sudo tail /var/log/kdzbot_java_srv.log
        echo ""
        echo "=============================================="
        echo "Backend and sidecars are ready."
        echo "Backend URL: ${BACKEND_URL}"
        echo "Auth server URL: ${AUTH_URL}"
        echo "=============================================="
        
        echo "Restarting KDZBOT py"
        sudo systemctl restart greenbich
        sudo systemctl status greenbich
        echo "Done."
        exit 0
    fi
    sudo tail -n1 /var/log/kdzbot_java_srv.log
    sleep 1
done

echo ""
echo "Error: Timeout waiting for backend/auth readiness."
exit 1
