/**
 * SSO Auth Service - Passport.js (session) + OpenID Connect (openid-client)
 * Handles login/logout; exposes /api/auth/me and /api/auth/token for the frontend.
 * Frontend sends the token as Bearer to the Java backend.
 */
import 'dotenv/config';
import express from 'express';
import session from 'express-session';
import { RedisStore } from 'connect-redis';
import { createClient } from 'redis';
import passport from 'passport';
import { Issuer, generators } from 'openid-client';
import cors from 'cors';
import { pathToFileURL } from 'url';

const PORT = Number(process.env.PORT) || 3002;
const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:9000';
const ISSUER_URI = process.env.ISSUER_URI || process.env.SSO_ISSUER_URI;
const CLIENT_ID = process.env.CLIENT_ID || process.env.SSO_CLIENT_ID;
const CLIENT_SECRET = process.env.CLIENT_SECRET || process.env.SSO_CLIENT_SECRET;
const SESSION_SECRET = process.env.SESSION_SECRET || 'dev-session-secret-change-in-prod';
const AUTH_SESSION_STORE = (process.env.AUTH_SESSION_STORE || 'redis').toLowerCase();
const AUTH_SERVICE_REDIS_URL = process.env.AUTH_SERVICE_REDIS_URL || 'redis://redis:6379';
const AUTH_SERVICE_REDIS_PREFIX = process.env.AUTH_SERVICE_REDIS_PREFIX || 'aisystem:auth:sess:';
const AUTH_SERVICE_URL = process.env.AUTH_SERVICE_URL || `http://localhost:${PORT}`;
const GENERIC_BACKEND_URL = process.env.GENERIC_BACKEND_URL || 'http://rig1.lan:8080';
const REMEMBER_ME_MAX_AGE_MS = Number(process.env.REMEMBER_ME_MAX_AGE_MS) || (30 * 24 * 60 * 60 * 1000);
const SERVER_PROFILE = (process.env.SERVER_PROFILE || '').toLowerCase();
const IS_DEV_PROFILE = SERVER_PROFILE === 'dev';
const GENERIC_BACKEND_BASE_URL = new URL(GENERIC_BACKEND_URL);
console.log("BE URL:", GENERIC_BACKEND_BASE_URL);

const hasOidcConfig = !!(ISSUER_URI && CLIENT_ID && CLIENT_SECRET);
if (!hasOidcConfig) {
  console.warn('SSO not configured: set ISSUER_URI, CLIENT_ID, CLIENT_SECRET for real IdP.');
}

passport.serializeUser((user, done) => done(null, user));
passport.deserializeUser((user, done) => done(null, user));

// Lazy OIDC client (discover issuer on first use)
let oidcClientPromise = null;
let redisClient = null;
let redisConnectStarted = false;

function getRedisClient() {
  if (redisClient) {
    return redisClient;
  }
  redisClient = createClient({ url: AUTH_SERVICE_REDIS_URL });
  redisClient.on('error', (err) => {
    console.error('Redis client error:', err);
  });
  if (!redisConnectStarted) {
    redisConnectStarted = true;
    redisClient.connect()
      .then(() => {
        console.log(`Auth service connected to Redis session store at ${AUTH_SERVICE_REDIS_URL}`);
      })
      .catch((err) => {
        console.error('Failed to connect auth service to Redis session store:', err);
        process.exit(1);
      });
  }
  return redisClient;
}
async function getOidcClient() {
  if (!hasOidcConfig) return null;
  if (!oidcClientPromise) {
    const issuer = await Issuer.discover(ISSUER_URI);
    const callbackURL = `${AUTH_SERVICE_URL}/auth/callback`;
    oidcClientPromise = Promise.resolve(new issuer.Client({
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
      redirect_uris: [callbackURL],
      response_types: ['code'],
    }));
  }
  return oidcClientPromise;
}

async function authenticateWithGenericBackend(username, password) {
  const response = await fetch(buildGenericBackendUrl('/api/users/authenticate'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });

  if (!response.ok) {
    return null;
  }

  return response.json();
}

function buildGenericBackendUrl(path) {
  return new URL(path, GENERIC_BACKEND_BASE_URL).toString();
}

async function changePasswordInGenericBackend(username, currentPassword, newPassword, bearerToken = null) {
  const headers = { 'Content-Type': 'application/json' };
  if (bearerToken) {
    headers.Authorization = `Bearer ${bearerToken}`;
  }
  const response = await fetch(buildGenericBackendUrl('/api/users/change-password'), {
    method: 'POST',
    headers,
    body: JSON.stringify({ username, currentPassword, newPassword }),
  });

  const payload = response.headers.get('content-type')?.includes('application/json')
    ? await response.json()
    : {};

  return { ok: response.ok, status: response.status, payload };
}

async function adminFetchFromGenericBackend(path, method = 'GET', body = null, bearerToken = null) {
  const headers = { 'Content-Type': 'application/json' };
  if (bearerToken) {
    headers.Authorization = `Bearer ${bearerToken}`;
  }
  const response = await fetch(buildGenericBackendUrl(path), {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  const payload = response.headers.get('content-type')?.includes('application/json')
    ? await response.json()
    : {};
  return { ok: response.ok, status: response.status, payload };
}

async function adminFetchFromGenericBackend2(path, method = 'GET', body = null, bearerToken = null, csrfToken = null) {
  const headers = { 'Content-Type': 'application/json' };
  if (bearerToken) {
    headers.Authorization = `Bearer ${bearerToken}`;
  }
  if(csrfToken){
    headers['X-XSRF-TOKEN'] = csrfToken    
  }
  const response = await fetch(buildGenericBackendUrl(path), {
    method,
    headers,
    body: body,
  });
  const payload = response.headers.get('content-type')?.includes('application/json')
    ? await response.json()
    : {};
  return { ok: response.ok, status: response.status, payload: payload };
}

function loginSession(req, user) {
  return new Promise((resolve, reject) => {
    req.login(user, (err) => {
      if (err) {
        reject(err);
        return;
      }
      resolve();
    });
  });
}

function saveSession(req) {
  return new Promise((resolve, reject) => {
    req.session.save((err) => {
      if (err) {
        reject(err);
        return;
      }
      resolve();
    });
  });
}

function applyRememberMeCookie(req, rememberMe) {
  if (!req.session || !req.session.cookie) {
    return;
  }
  if (rememberMe) {
    req.session.cookie.maxAge = REMEMBER_ME_MAX_AGE_MS;
    req.session.rememberMe = true;
    return;
  }
  req.session.cookie.expires = null;
  req.session.cookie.maxAge = null;
  req.session.rememberMe = false;
}

export function createApp() {
  const app = express();
  console.log(`FE URL: '${FRONTEND_URL}'`);

  app.use(cors({
    origin: [FRONTEND_URL, 'http://rig1.lan:8088', 'http://rig1.lan:9000'],
    credentials: true,
    methods: ['GET', 'POST', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization'],
  }));
  app.use(express.json());

  const sessionConfig = {
    secret: SESSION_SECRET,
    resave: false,
    saveUninitialized: false,
    cookie: {
      secure: false, // Allow HTTP (behind reverse proxy)
      httpOnly: true,
      sameSite: 'lax', // Less strict for cross-origin
      domain: 'rig1.lan', // Explicit domain for subdomains/ports
      maxAge: 24 * 60 * 60 * 1000000, // 24000 hours
      path: '/', // Cookie valid for entire domain
    },
  };

  if (AUTH_SESSION_STORE === 'memory') {
    console.warn('Auth service uses in-memory sessions (AUTH_SESSION_STORE=memory). Sessions will be lost on restart.');
  } else if (AUTH_SESSION_STORE === 'redis') {
    const client = getRedisClient();
    sessionConfig.store = new RedisStore({
      client,
      prefix: AUTH_SERVICE_REDIS_PREFIX,
    });
  } else {
    throw new Error(`Unsupported AUTH_SESSION_STORE value: ${AUTH_SESSION_STORE}`);
  }

  app.use(session(sessionConfig));

  app.use(passport.initialize());
  app.use(passport.session());

  function ensureSecureCredentialTransport(req, res, next) {
  /*
      211 -    if (IS_DEV_PROFILE) {                                                                                                                                                                                                   
      212 -      return next();                                                                                                                                                                                                        
      213 -    }                                                                                                                                                                                                                       
      214 -    const forwardedProto = req.get('x-forwarded-proto');                                                                                                                                                                    
      215 -    const isSecure = req.secure || forwardedProto === 'https';                                                                                                                                                              
      216 -    if (!isSecure) {                                                                                                                                                                                                        
      217 -      return res.status(400).json({                                                                                                                                                                                         
      218 -        error: 'Sensitive authentication endpoints require HTTPS transport',                                                                                                                                                
      219 -      });                                                                                                                                                                                                                   
      220 -    }         
  */
    // Allow HTTP in dev and prod (behind reverse proxy)
    return next();
  }

  function requireAdmin(req, res, next) {
    if (!req.isAuthenticated() || !req.user) {
      return res.status(401).json({ error: 'Not authenticated' });
    }
    if (req.user.role !== 'admin') {
      return res.status(403).json({ error: 'Admin role required' });
    }
    return next();
  }

  /*
  // ----- Routes (Passport.js session + openid-client flow) -----
  app.get('/auth/login', ensureSecureCredentialTransport, async (req, res) => {
    const rememberParam = String(req.query.remember || '').toLowerCase();
    if (rememberParam === '1' || rememberParam === 'true' || rememberParam === 'yes') {
      req.session.rememberMe = true;
    }
    if (!hasOidcConfig) {
      return res.redirect(`${FRONTEND_URL}/#/login?logged_in=dev&mock=1`);
    }
    try {
      const client = await getOidcClient();
      const codeVerifier = generators.codeVerifier();
      const codeChallenge = generators.codeChallenge(codeVerifier);
      const state = generators.state();
      const nonce = generators.nonce();
      req.session.oidc = { codeVerifier, state, nonce };
      const authUrl = client.authorizationUrl({
        scope: 'openid profile email',
        code_challenge: codeChallenge,
        code_challenge_method: 'S256',
        state,
        nonce,
      });
      res.redirect(authUrl);
    } catch (err) {
      console.error('Auth login error:', err);
      res.redirect(`${FRONTEND_URL}/#/login?error=config`);
    }
  });
*/
  app.get('/auth/callback', ensureSecureCredentialTransport, async (req, res) => {
    if (!hasOidcConfig) {
      return res.redirect(`${FRONTEND_URL}/#/login?error=no_sso_config`);
    }
    const { codeVerifier, state, nonce } = req.session.oidc || {};
    if (!codeVerifier || !state) {
      return res.redirect(`${FRONTEND_URL}/#/login?error=session_restart`);
    }
    try {
      const client = await getOidcClient();
      const callbackURL = `${AUTH_SERVICE_URL}/auth/callback`;
      const params = client.callbackParams(req);
      const tokenSet = await client.callback(callbackURL, params, {
        state,
        code_verifier: codeVerifier,
        nonce,
      });
      delete req.session.oidc;

      let userInfo = {};
      try {
        userInfo = await client.userinfo(tokenSet);
      } catch (e) {
        userInfo = tokenSet.claims || {};
      }
      const user = {
        id: userInfo.sub || userInfo.id,
        name: userInfo.name || userInfo.preferred_username || userInfo.email || 'User',
        email: userInfo.email || userInfo.preferred_username,
        role: 'maintainer',
        accessToken: tokenSet.access_token,
        idToken: tokenSet.id_token,
      };
      await loginSession(req, user);
      applyRememberMeCookie(req, !!req.session.rememberMe);
      await saveSession(req);
      res.redirect(`${FRONTEND_URL}/#/login?logged_in=1`);
    } catch (err) {
      console.error('OIDC callback error:', err);
      delete req.session.oidc;
      res.redirect(`${FRONTEND_URL}/#/login?error=auth_failed`);
    }
  });

  app.post('/api/auth/login', ensureSecureCredentialTransport, async (req, res) => {
    const username = typeof req.body?.username === 'string' ? req.body.username.trim() : '';
    const password = typeof req.body?.password === 'string' ? req.body.password : '';
    const rememberMe = req.body?.rememberMe === true;
    if (!username || !password) {
      return res.status(400).json({ error: 'Username and password are required' });
    }

    try {
      const backendUser = await authenticateWithGenericBackend(username, password);
      if (!backendUser) {
        return res.status(401).json({ error: 'Invalid credentials' });
      }

      const user = {
        id: backendUser.id,
        name: backendUser.username || username,
        username: backendUser.username || username,
        email: `${backendUser.username || username}@local`,
        role: backendUser.role || 'maintainer',
        accessToken: backendUser.accessToken || null,
        idToken: backendUser.idToken || null,
      };
      await loginSession(req, user);
      applyRememberMeCookie(req, rememberMe);
      await saveSession(req);
      return res.json({ ok: true, user, rememberMe });
    } catch (error) {
      const backendAuthUrl = `${GENERIC_BACKEND_URL}/api/users/authenticate`;
      // Keep raw error object in logs for full diagnostics (including socket/IP details).
      console.error(`Local login backend call failed at ${backendAuthUrl}:`, error);
      const errorCode = error?.code || 'UNKNOWNERRORCODE';
      return res.status(502).json({
        error: `Authentication backend unavailable at configured endpoint ${backendAuthUrl} (${errorCode})`,
      });
    }
  });

  app.get('/auth/logout', ensureSecureCredentialTransport, (req, res) => {
    req.logout((err) => {
      if (err) console.error('Logout error:', err);
      req.session.destroy(() => {
        res.redirect(`${FRONTEND_URL}/#/login`);
      });
    });
  });

  app.get('/api/auth/me', (req, res) => {
    if (!req.isAuthenticated || !req.isAuthenticated() || !req.user) {
      return res.status(401).json({ error: 'Not authenticated' });
    }
    res.json({
      id: req.user.id,
      name: req.user.name,
      email: req.user.email,
      role: req.user.role || 'maintainer',
    });
  });

  app.get('/api/auth/token', (req, res) => {
    if (!req.isAuthenticated || !req.isAuthenticated() || !req.user) {
      return res.status(401).json({ error: 'Not authenticated' });
    }
    const token = req.user.accessToken || req.user.idToken;
    if (!token) {
      return res.status(401).json({ error: 'No token in session' });
    }
    res.json({ accessToken: token });
  });

  app.post('/api/auth/change-password', ensureSecureCredentialTransport, async (req, res) => {
    if (!req.isAuthenticated || !req.isAuthenticated() || !req.user) {
      return res.status(401).json({ error: 'Not authenticated' });
    }

    const currentPassword = typeof req.body?.currentPassword === 'string' ? req.body.currentPassword : '';
    const newPassword = typeof req.body?.newPassword === 'string' ? req.body.newPassword : '';
    if (!currentPassword || !newPassword) {
      return res.status(400).json({ error: 'Current password and new password are required' });
    }
    if (newPassword.length < 8) {
      return res.status(400).json({ error: 'New password must be at least 8 characters' });
    }

    const username = req.user.username
      || (typeof req.user.email === 'string' && req.user.email.includes('@')
        ? req.user.email.split('@')[0]
        : req.user.name);
    if (!username) {
      return res.status(400).json({ error: 'Cannot resolve current user account' });
    }

    try {
      const bearerToken = req.user.accessToken || req.user.idToken || null;
      const result = await changePasswordInGenericBackend(username, currentPassword, newPassword, bearerToken);
      if (!result.ok) {
        return res.status(result.status).json(result.payload?.error
          ? { error: result.payload.error }
          : { error: 'Password change failed' });
      }
      return res.json({ ok: true, message: result.payload?.message || 'Password changed successfully' });
    } catch (error) {
      console.error('Change password error:', error);
      return res.status(502).json({ error: 'Authentication backend unavailable' });
    }
  });

  app.get('/api/admin/users', ensureSecureCredentialTransport, async (req, res) => {
	if (!req.isAuthenticated || !req.isAuthenticated() || !req.user) {
	  console.log("req:", req);
	  return res.status(401).json({ error: 'Not authenticated' });
	}
    const page = Number.isFinite(Number(req.query.page)) ? Number(req.query.page) : 0;
    const size = Number.isFinite(Number(req.query.size)) ? Number(req.query.size) : 10;
    const username = typeof req.query.username === 'string' ? req.query.username : '';
    try {
      const bearerToken = req.user.accessToken || req.user.idToken || null;
	  console.log("calling BE, bearer:", bearerToken);
      const result = await adminFetchFromGenericBackend(
        `/api/users?page=${Math.max(page, 0)}&size=${Math.max(size, 1)}&username=${encodeURIComponent(username)}`,
        'GET',
        null,
        bearerToken
      );
	  console.log("ret status", result.status, "payload", result.payload);
      return res.status(result.status).json(result.payload);
    } catch (error) {
      console.error('Admin list users error:', error);
      return res.status(502).json({ error: 'Backend unavailable' });
    }
  });

  app.post('/api/admin/users', requireAdmin, ensureSecureCredentialTransport, async (req, res) => {
    const username = typeof req.body?.username === 'string' ? req.body.username.trim() : '';
    const role = typeof req.body?.role === 'string' ? req.body.role.trim() : '';
    const password = typeof req.body?.password === 'string' ? req.body.password : '';
    if (!username || !role || !password) {
      return res.status(400).json({ error: 'Username, role and password are required' });
    }
    try {
      const bearerToken = req.user.accessToken || req.user.idToken || null;
      const result = await adminFetchFromGenericBackend('/api/users', 'POST', { username, role, password }, bearerToken);
      return res.status(result.status).json(result.payload);
    } catch (error) {
      console.error('Admin create user error:', error);
      return res.status(502).json({ error: 'Backend unavailable' });
    }
  });

  app.put('/api/admin/users/:id', requireAdmin, ensureSecureCredentialTransport, async (req, res) => {
    const id = Number(req.params.id);
    if (!Number.isFinite(id)) {
      return res.status(400).json({ error: 'Invalid user id' });
    }
    const username = typeof req.body?.username === 'string' ? req.body.username.trim() : '';
    const role = typeof req.body?.role === 'string' ? req.body.role.trim() : '';
    const password = typeof req.body?.password === 'string' ? req.body.password : '';
    if (!username || !role) {
      return res.status(400).json({ error: 'Username and role are required' });
    }
    try {
      const bearerToken = req.user.accessToken || req.user.idToken || null;
      const result = await adminFetchFromGenericBackend(`/api/users/${id}`, 'PUT', { username, role, password }, bearerToken);
      return res.status(result.status).json(result.payload);
    } catch (error) {
      console.error('Admin update user error:', error);
      return res.status(502).json({ error: 'Backend unavailable' });
    }
  });

  app.delete('/api/admin/users/:id', requireAdmin, async (req, res) => {
    const id = Number(req.params.id);
    if (!Number.isFinite(id)) {
      return res.status(400).json({ error: 'Invalid user id' });
    }
    try {
      const bearerToken = req.user.accessToken || req.user.idToken || null;
      const result = await adminFetchFromGenericBackend(`/api/users/${id}`, 'DELETE', null, bearerToken);
      if (!result.ok && result.status !== 204) {
        return res.status(result.status).json(result.payload?.error ? { error: result.payload.error } : { error: 'Delete failed' });
      }
      return res.status(204).send();
    } catch (error) {
      console.error('Admin delete user error:', error);
      return res.status(502).json({ error: 'Backend unavailable' });
    }
  });

  // Passthrough to Java backend for LLM endpoints
  app.post('/api/llm-endpoints', async (req, res) => {
    console.log('/api/llm-endpoints');
  const userId = req.query.userId;
    try {
      const bearerToken = null;
      const csrfToken = req.headers['X-XSRF-TOKEN']
	  const payload1 = req.headers['content-type']?.includes('application/json')
	    ? await req.body
	    : {};
	  const response = await adminFetchFromGenericBackend2(`/api/llm-endpoints${userId !== undefined ? `?userId=${userId}` : ''}`, 'POST', 
		JSON.stringify(payload1), bearerToken, csrfToken);
      const payload = await response.payload;
      return res.status(response.status).json(payload);
    } catch (error) {
      console.error('LLM endpoints passthrough error:', error);
      return res.status(502).json({ error: 'Backend unavailable' });
    }
  });

  app.get('/api/llm-endpoints', async (req, res) => {
    console.log('/api/llm-endpoints');
  const userId = req.query.userId;
    try {
    
      const response = await fetch(buildGenericBackendUrl(`/api/llm-endpoints${userId !== undefined ? `?userId=${userId}` : ''}`));
      const payload = await response.json().catch(() => ({}));
      return res.status(response.status).json(payload);
    } catch (error) {
      console.error('LLM endpoints passthrough error:', error);
      return res.status(502).json({ error: 'Backend unavailable' });
    }
  });

  app.get('/health', (req, res) => {
    res.json({ status: 'UP', service: 'auth' });
  });

  return app;
}

export function startServer() {
  const app = createApp();
  return app.listen(PORT, () => {
    console.log(`Auth service (Passport.js session + OIDC) at http://localhost:${PORT}`);
    console.log(`Frontend URL: ${FRONTEND_URL}`);
  });
}

const executedPath = process.argv[1] ? pathToFileURL(process.argv[1]).href : '';
if (import.meta.url === executedPath) {
  startServer();
}
