import { describe, it, expect, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useAuthStore } from '../../src/stores/auth';

describe('Auth Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    (globalThis as { fetch?: typeof fetch }).fetch = undefined;
  });

  it('initializes with unauthenticated state', () => {
    const auth = useAuthStore();
    expect(auth.isAuthenticated).toBe(false);
    expect(auth.isLoggedIn).toBe(false);
    expect(auth.user).toBeNull();
  });

  it('sets authenticated state after login', async () => {
    (globalThis as { fetch: typeof fetch }).fetch = async () => ({
      ok: true,
      async json() {
        return {
          ok: true,
          user: { id: '1', name: 'admin', email: 'admin@local', role: 'admin' },
        };
      },
    } as Response);

    const auth = useAuthStore();
    const result = await auth.login('admin', 'admin');
    expect(result).toBe(true);
    expect(auth.isAuthenticated).toBe(true);
    expect(auth.isLoggedIn).toBe(true);
    expect(auth.user?.name).toBe('admin');
    expect(auth.user?.role).toBe('admin');
    expect(auth.isAdmin).toBe(true);
  });

  it('clears state after logout', async () => {
    (globalThis as { fetch: typeof fetch }).fetch = async () => ({
      ok: true,
      async json() {
        return {
          ok: true,
          user: { id: '2', name: 'maintainer', email: 'maintainer@local', role: 'maintainer' },
        };
      },
    } as Response);

    const auth = useAuthStore();
    await auth.login('maintainer', 'password123');
    const loc = { href: '' };
    Object.defineProperty(window, 'location', { value: loc, configurable: true });
    await auth.logout();
    expect(auth.isAuthenticated).toBe(false);
    expect(auth.isLoggedIn).toBe(false);
    expect(auth.user).toBeNull();
    expect(loc.href).toContain('/auth/logout');
  });

  it('registers a new user', async () => {
    const auth = useAuthStore();
    const result = await auth.signup('John Doe', 'john@example.com', 'password123');
    expect(result).toBe(true);
    expect(auth.isAuthenticated).toBe(true);
    expect(auth.user?.name).toBe('John Doe');
    expect(auth.user?.role).toBe('maintainer');
  });

  it('changes password successfully for authenticated session', async () => {
    (globalThis as { fetch: typeof fetch }).fetch = async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/auth/login')) {
        return {
          ok: true,
          async json() {
            return {
              ok: true,
              user: { id: '1', name: 'admin', email: 'admin@local', role: 'admin' },
            };
          },
        } as Response;
      }
      if (url.includes('/api/auth/change-password')) {
        return {
          ok: true,
          async json() {
            return { ok: true };
          },
        } as Response;
      }
      throw new Error(`Unexpected request URL: ${url}`);
    };

    const auth = useAuthStore();
    await auth.login('admin', 'admin');
    const result = await auth.changePassword('admin', 'admin1234');
    expect(result.ok).toBe(true);
  });

  it('returns error when password change fails', async () => {
    (globalThis as { fetch: typeof fetch }).fetch = async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/auth/change-password')) {
        return {
          ok: false,
          async json() {
            return { error: 'Current password is incorrect' };
          },
        } as Response;
      }
      return {
        ok: true,
        async json() {
          return { ok: true };
        },
      } as Response;
    };

    const auth = useAuthStore();
    const result = await auth.changePassword('bad', 'newPassword1');
    expect(result.ok).toBe(false);
    expect(result.error).toContain('Current password is incorrect');
  });
});
