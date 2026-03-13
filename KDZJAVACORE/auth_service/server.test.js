import test from 'node:test';
import assert from 'node:assert/strict';
import request from 'supertest';
import { createApp } from './server.js';

function mockFetchWith(responseFactory) {
  const originalFetch = global.fetch;
  global.fetch = responseFactory;
  return () => {
    global.fetch = originalFetch;
  };
}

test('local login stores session and exposes role in /api/auth/me', async () => {
  const restoreFetch = mockFetchWith(async () => ({
    ok: true,
    async json() {
      return { id: 1, username: 'admin', role: 'admin' };
    },
  }));

  const app = createApp();
  const agent = request.agent(app);

  const loginResponse = await agent
    .post('/api/auth/login')
    .set('x-forwarded-proto', 'https')
    .send({ username: 'admin', password: 'admin' });

  assert.equal(loginResponse.status, 200);
  assert.equal(loginResponse.body.user.role, 'admin');

  const meResponse = await agent.get('/api/auth/me');
  assert.equal(meResponse.status, 200);
  assert.equal(meResponse.body.role, 'admin');

  restoreFetch();
});

test('local login with rememberMe sets persistent session cookie', async () => {
  const restoreFetch = mockFetchWith(async () => ({
    ok: true,
    async json() {
      return { id: 21, username: 'remember-admin', role: 'admin' };
    },
  }));

  const app = createApp();
  const response = await request(app)
    .post('/api/auth/login')
    .set('x-forwarded-proto', 'https')
    .send({ username: 'remember-admin', password: 'admin', rememberMe: true });

  assert.equal(response.status, 200);
  assert.equal(response.body.rememberMe, true);
  const setCookieHeader = (response.headers['set-cookie'] || []).join('; ');
  assert.ok(
    /Max-Age=/i.test(setCookieHeader) || /Expires=/i.test(setCookieHeader),
    'Expected persistent cookie attributes when rememberMe is true'
  );

  restoreFetch();
});

test('local login without rememberMe sets session cookie', async () => {
  const restoreFetch = mockFetchWith(async () => ({
    ok: true,
    async json() {
      return { id: 22, username: 'short-admin', role: 'admin' };
    },
  }));

  const app = createApp();
  const response = await request(app)
    .post('/api/auth/login')
    .set('x-forwarded-proto', 'https')
    .send({ username: 'short-admin', password: 'admin', rememberMe: false });

  assert.equal(response.status, 200);
  const setCookieHeader = (response.headers['set-cookie'] || []).join('; ');
  assert.ok(!/Max-Age=/i.test(setCookieHeader), 'Expected non-persistent session cookie when rememberMe is false');

  restoreFetch();
});

test('local login returns 401 for invalid credentials', async () => {
  const restoreFetch = mockFetchWith(async () => ({ ok: false }));

  const app = createApp();
  const response = await request(app)
    .post('/api/auth/login')
    .set('x-forwarded-proto', 'https')
    .send({ username: 'admin', password: 'wrong' });

  assert.equal(response.status, 401);

  restoreFetch();
});

test('local login returns 400 when username/password are missing', async () => {
  const app = createApp();

  const response = await request(app)
    .post('/api/auth/login')
    .set('x-forwarded-proto', 'https')
    .send({ username: '', password: '' });

  assert.equal(response.status, 400);
  assert.match(response.body.error, /required/i);
});

test('local login rejects insecure transport for credentials', async () => {
  const app = createApp();

  const response = await request(app)
    .post('/api/auth/login')
    .send({ username: 'admin', password: 'admin' });

  assert.equal(response.status, 400);
  assert.match(response.body.error, /https/i);
});

test('local login returns 502 when backend is unavailable', async () => {
  const restoreFetch = mockFetchWith(async () => {
    throw new Error('backend down');
  });

  const app = createApp();
  const response = await request(app)
    .post('/api/auth/login')
    .set('x-forwarded-proto', 'https')
    .send({ username: 'admin', password: 'admin' });

  assert.equal(response.status, 502);
  assert.match(response.body.error, /unavailable/i);
  restoreFetch();
});

test('unauthenticated /api/auth/me and /api/auth/token return 401', async () => {
  const app = createApp();
  const meResponse = await request(app).get('/api/auth/me');
  const tokenResponse = await request(app).get('/api/auth/token');

  assert.equal(meResponse.status, 401);
  assert.equal(tokenResponse.status, 401);
});

test('local login session without token returns 401 on /api/auth/token', async () => {
  const restoreFetch = mockFetchWith(async () => ({
    ok: true,
    async json() {
      return { id: 2, username: 'maint', role: 'maintainer' };
    },
  }));

  const app = createApp();
  const agent = request.agent(app);
  const loginResponse = await agent
    .post('/api/auth/login')
    .set('x-forwarded-proto', 'https')
    .send({ username: 'maint', password: 'secret' });

  assert.equal(loginResponse.status, 200);

  const tokenResponse = await agent.get('/api/auth/token');
  assert.equal(tokenResponse.status, 401);
  assert.match(tokenResponse.body.error, /no token/i);
  restoreFetch();
});

test('health endpoint reports auth service UP', async () => {
  const app = createApp();
  const response = await request(app).get('/health');
  assert.equal(response.status, 200);
  assert.deepEqual(response.body, { status: 'UP', service: 'auth' });
});

test('change-password rejects unauthenticated requests', async () => {
  const app = createApp();
  const response = await request(app)
    .post('/api/auth/change-password')
    .set('x-forwarded-proto', 'https')
    .send({ currentPassword: 'old', newPassword: 'newPassword1' });

  assert.equal(response.status, 401);
});

test('change-password updates password for authenticated session', async () => {
  let authHeader = null;
  const restoreFetch = mockFetchWith(async (url, options = {}) => {
    if (typeof url === 'string' && url.endsWith('/api/users/authenticate')) {
      return {
        ok: true,
        async json() {
          return { id: 10, username: 'admin', role: 'admin', accessToken: 'jwt-admin-token' };
        },
      };
    }
    if (typeof url === 'string' && url.endsWith('/api/users/change-password')) {
      authHeader = options?.headers?.Authorization || null;
      return {
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        async json() {
          return { message: 'Password changed successfully' };
        },
      };
    }
    throw new Error(`Unexpected URL: ${String(url)}`);
  });

  const app = createApp();
  const agent = request.agent(app);
  const loginResponse = await agent
    .post('/api/auth/login')
    .set('x-forwarded-proto', 'https')
    .send({ username: 'admin', password: 'admin' });
  assert.equal(loginResponse.status, 200);

  const response = await agent
    .post('/api/auth/change-password')
    .set('x-forwarded-proto', 'https')
    .send({ currentPassword: 'admin', newPassword: 'admin1234' });
  assert.equal(response.status, 200);
  assert.equal(response.body.ok, true);
  assert.equal(authHeader, 'Bearer jwt-admin-token');

  restoreFetch();
});

test('admin users endpoint rejects non-admin sessions', async () => {
  const restoreFetch = mockFetchWith(async () => ({
    ok: true,
    async json() {
      return { id: 11, username: 'maint', role: 'maintainer' };
    },
  }));

  const app = createApp();
  const agent = request.agent(app);
  const loginResponse = await agent
    .post('/api/auth/login')
    .set('x-forwarded-proto', 'https')
    .send({ username: 'maint', password: 'secret' });
  assert.equal(loginResponse.status, 200);

  const response = await agent.get('/api/admin/users');
  assert.equal(response.status, 403);
  restoreFetch();
});

test('admin users endpoint returns paginated user list for admin', async () => {
  const restoreFetch = mockFetchWith(async (url) => {
    if (typeof url === 'string' && url.includes('/api/users/authenticate')) {
      return {
        ok: true,
        async json() {
          return { id: 1, username: 'admin', role: 'admin' };
        },
      };
    }
    if (typeof url === 'string' && url.includes('/api/users?page=')) {
      return {
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        async json() {
          return {
            items: [{ id: 1, username: 'admin', role: 'admin' }],
            page: 0,
            size: 10,
            totalElements: 1,
            totalPages: 1,
          };
        },
      };
    }
    throw new Error(`Unexpected URL: ${String(url)}`);
  });

  const app = createApp();
  const agent = request.agent(app);
  const loginResponse = await agent
    .post('/api/auth/login')
    .set('x-forwarded-proto', 'https')
    .send({ username: 'admin', password: 'admin' });
  assert.equal(loginResponse.status, 200);

  const response = await agent.get('/api/admin/users?page=0&size=10&username=ad');
  assert.equal(response.status, 200);
  assert.equal(response.body.items[0].username, 'admin');
  restoreFetch();
});

test('admin can create, update and delete user via auth service proxy', async () => {
  const calls = [];
  const restoreFetch = mockFetchWith(async (url, options = {}) => {
    const requestUrl = String(url);
    calls.push({ requestUrl, method: options.method || 'GET' });
    if (requestUrl.includes('/api/users/authenticate')) {
      return {
        ok: true,
        async json() {
          return { id: 1, username: 'admin', role: 'admin' };
        },
      };
    }
    if (requestUrl.endsWith('/api/users') && options.method === 'POST') {
      return {
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        async json() {
          return { id: 99, username: 'newuser', role: 'maintainer' };
        },
      };
    }
    if (requestUrl.endsWith('/api/users/99') && options.method === 'PUT') {
      return {
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        async json() {
          return { id: 99, username: 'updateduser', role: 'admin' };
        },
      };
    }
    if (requestUrl.endsWith('/api/users/99') && options.method === 'DELETE') {
      return {
        ok: true,
        status: 204,
        headers: new Headers(),
        async json() {
          return {};
        },
      };
    }
    throw new Error(`Unexpected URL: ${requestUrl} method=${options.method || 'GET'}`);
  });

  const app = createApp();
  const agent = request.agent(app);
  const loginResponse = await agent
    .post('/api/auth/login')
    .set('x-forwarded-proto', 'https')
    .send({ username: 'admin', password: 'admin' });
  assert.equal(loginResponse.status, 200);

  const createResponse = await agent
    .post('/api/admin/users')
    .set('x-forwarded-proto', 'https')
    .send({ username: 'newuser', role: 'maintainer', password: 'tmp1234' });
  assert.equal(createResponse.status, 200);
  assert.equal(createResponse.body.username, 'newuser');

  const updateResponse = await agent
    .put('/api/admin/users/99')
    .set('x-forwarded-proto', 'https')
    .send({ username: 'updateduser', role: 'admin', password: 'tmp9999' });
  assert.equal(updateResponse.status, 200);
  assert.equal(updateResponse.body.username, 'updateduser');

  const deleteResponse = await agent.delete('/api/admin/users/99');
  assert.equal(deleteResponse.status, 204);

  assert.ok(calls.some(entry => entry.requestUrl.endsWith('/api/users') && entry.method === 'POST'));
  assert.ok(calls.some(entry => entry.requestUrl.endsWith('/api/users/99') && entry.method === 'PUT'));
  assert.ok(calls.some(entry => entry.requestUrl.endsWith('/api/users/99') && entry.method === 'DELETE'));
  restoreFetch();
});
