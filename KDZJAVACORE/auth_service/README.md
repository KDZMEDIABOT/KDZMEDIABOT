# Auth Service (Passport.js + OpenID Connect)

SSO is handled by this Node.js service using **Passport.js** for session management and **openid-client** for the OpenID Connect flow. The Java backend no longer performs the login redirect; it only validates JWT Bearer tokens from the same IdP.

## Flow

1. User clicks “Sign in with SSO” in the frontend → redirect to `GET /auth/login`.
2. Auth service redirects to the IdP (authorization code + PKCE).
3. IdP redirects back to `GET /auth/callback`; auth service exchanges the code for tokens and userinfo, stores them in session (Passport.js session), redirects to frontend with `?logged_in=1`.
4. Frontend calls `GET /api/auth/token` (with credentials) to get the access token, stores it, and sends it as `Authorization: Bearer <token>` to the Java backend.
5. Java backend (OAuth2 resource server) validates the JWT using the same IdP’s issuer.

## Setup

```bash
cd auth_service
cp .env.example .env
# Edit .env: ISSUER_URI, CLIENT_ID, CLIENT_SECRET, SESSION_SECRET, FRONTEND_URL
npm install
npm start
```

- **PORT**: Auth service port (default 3001).
- **FRONTEND_URL**: Quasar app URL (e.g. `http://localhost:9000`).
- **AUTH_SERVICE_URL**: Public URL of this service (for IdP callback).
- **ISSUER_URI**, **CLIENT_ID**, **CLIENT_SECRET**: Same IdP as backend `SSO_ISSUER_URI` / `SSO_CLIENT_ID` / `SSO_CLIENT_SECRET`.
- **SESSION_SECRET**: Secret for signing session cookies.
- **AUTH_SESSION_STORE**: `redis` (default) or `memory` (tests/dev-only).
- **AUTH_SERVICE_REDIS_URL**: Redis connection URL for persistent session storage.
- **AUTH_SERVICE_REDIS_PREFIX**: Prefix for session keys in Redis.

If `ISSUER_URI` / `CLIENT_ID` / `CLIENT_SECRET` are not set, `/auth/login` redirects to the frontend with `?logged_in=dev&mock=1` (no real IdP).

## Frontend

Set `VITE_AUTH_SERVICE_URL` (e.g. `http://localhost:3001`) so the app redirects to this service for SSO. Default is `http://localhost:3001`.

## Backend

- Use **OAuth2 resource server** with `spring.security.oauth2.resourceserver.jwt.issuer-uri` set to the same IdP.
- Leave `issuer-uri` unset in dev to allow unauthenticated API access (e.g. for frontend tests).

## Remember Me persistence

`rememberMe` requires persistent server-side sessions. Configure Redis (`AUTH_SESSION_STORE=redis`) so auth sessions survive backend/auth sidecar restarts. In-memory sessions are volatile and are lost on process restart.
