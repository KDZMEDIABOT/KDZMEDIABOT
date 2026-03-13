# Docker Compose – Dev and Prod

## Development (`docker-compose.dev.yml`)

Runs **PostgreSQL**, **backend** (Spring Boot), and **auth service** (Passport.js). Run the frontend on the host for hot reload.

```bash
# From project root
docker compose -f docker-compose.dev.yml up -d

# Frontend on host (port 9000)
cd frontend && npm install && npm run dev
```

- **PostgreSQL**: `localhost:5432`, user `cust1dev`, DB `aisystem_dev`
- **Backend**: `http://localhost:8080`
- **Auth**: `http://localhost:3001`
- **Frontend**: `http://localhost:9000` (from host)

Optional: set `SSO_ISSUER_URI` (and related) in the `backend` service for JWT validation. Leave empty for local dev without an IdP.

Stop:

```bash
docker compose -f docker-compose.dev.yml down
```

Data is kept in volume `postgres_dev_data`. Use `down -v` to remove it.

---

## Production (`docker-compose.prod.yml`)

Runs **PostgreSQL**, **backend**, **auth**, and **frontend** (nginx). All traffic goes through the frontend container (port 80).

```bash
# Set required env (or use .env.prod)
export DB_PASSWORD=...
export SESSION_SECRET=...
export FRONTEND_URL=https://your-domain.com
export AUTH_SERVICE_URL=https://your-domain.com  # or auth service URL
# Optional SSO
export SSO_ISSUER_URI=...
export SSO_CLIENT_ID=...
export SSO_CLIENT_SECRET=...

docker compose -f docker-compose.prod.yml up -d
```

- **Frontend**: `http://localhost:80` (proxies `/api/` → backend, `/auth-api/` → auth)
- Backend and auth are not published; only the frontend is exposed.

Build with custom auth URL for the SPA:

```bash
docker compose -f docker-compose.prod.yml build --build-arg VITE_AUTH_SERVICE_URL=/auth-api
```

Stop:

```bash
docker compose -f docker-compose.prod.yml down
```

---

## Build context

- **Backend**: built from **project root** (`context: .`, `dockerfile: customer_project/Dockerfile`) so both `generic_backend` and `customer_project` are available.
- **Frontend** and **auth**: built from `frontend/` and `auth_service/` respectively.
