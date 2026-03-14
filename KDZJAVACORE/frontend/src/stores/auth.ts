import { defineStore } from 'pinia';
import { ref, computed } from 'vue';

const AUTH_SERVICE_URL = (import.meta as ImportMeta & { env: { VITE_AUTH_SERVICE_URL?: string } }).env?.VITE_AUTH_SERVICE_URL || 'http://localhost:3001';
const TOKEN_KEY = 'aisystem_bearer_token';

export const useAuthStore = defineStore('auth', () => {
  const isAuthenticated = ref(false);
  const user = ref<{ id?: string; name: string; email: string; role: string } | null>(null);
  const accessToken = ref<string | null>(null);
  const loading = ref(false);

  const isLoggedIn = computed(() => isAuthenticated.value);
  const currentUser = computed(() => user.value);
  const isAdmin = computed(() => user.value?.role === 'admin');
  const isMaintainer = computed(() => user.value?.role === 'maintainer');

  type LoginResult = {
    ok: boolean;
    status?: number;
    error?: string;
    backendError?: string;
  };

  /** Fetch token and user from Passport.js auth service (with credentials for session cookie). */
  async function fetchFromAuthService(): Promise<boolean> {
    loading.value = true;
    try {
      const meRes = await fetch(`${AUTH_SERVICE_URL}/api/auth/me`, { credentials: 'include' });
      if (!meRes.ok) {
        clearAuth();
        return false;
      }
      const me = await meRes.json();
      const tokenRes = await fetch(`${AUTH_SERVICE_URL}/api/auth/token`, { credentials: 'include' });
      const tokenData = tokenRes.ok ? await tokenRes.json() : {};
      const token = tokenData.accessToken || null;
      user.value = {
        id: me.id,
        name: me.name,
        email: me.email,
        role: me.role || 'maintainer',
      };
      accessToken.value = token;
      if (token) {
        try { sessionStorage.setItem(TOKEN_KEY, token); } catch { /* ignore */ }
      } else {
        try { sessionStorage.removeItem(TOKEN_KEY); } catch { /* ignore */ }
      }
      isAuthenticated.value = true;
      return true;
    } catch {
      clearAuth();
      return false;
    } finally {
      loading.value = false;
    }
  }

  function clearAuth(): void {
    isAuthenticated.value = false;
    user.value = null;
    accessToken.value = null;
    try { sessionStorage.removeItem(TOKEN_KEY); } catch { /* ignore */ }
  }

  /** Call after SSO redirect when URL has ?logged_in=1 or ?logged_in=dev */
  async function initFromSSOCallback(): Promise<boolean> {
    return fetchFromAuthService();
  }

  /** Redirect to Passport.js auth service for SSO login */
  function loginWithSSO(): void {
    window.location.href = `${AUTH_SERVICE_URL}/auth/login`;
  }

  /** Logout: clear local state and redirect to auth service logout (clears session) */
  async function logout(): Promise<void> {
    clearAuth();
    window.location.href = `${AUTH_SERVICE_URL}/auth/logout`;
  }

  /** Get Bearer token for API calls (null if not logged in) */
  function getBearerToken(): string | null {
    return accessToken.value;
  }

  async function login(username: string, password: string, rememberMe = false): Promise<LoginResult> {
    loading.value = true;
    try {
      const response = await fetch(`${AUTH_SERVICE_URL}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ username, password, rememberMe }),
      });
      if (!response.ok) {
        const payload = await response.json().catch(() => ({}));
        const backendError = typeof payload?.error === 'string' ? payload.error : undefined;
        clearAuth();
        return {
          ok: false,
          status: response.status,
          backendError,
          error: buildDetailedLoginError(response.status, backendError),
        };
      }
      // Consume JSON body to keep fetch semantics explicit, then hydrate auth
      // from the sidecar session so reloads can restore state consistently.
      await response.json().catch(() => ({}));
      const restored = await fetchFromAuthService();
      if (!restored) {
        clearAuth();
        return {
          ok: false,
          error: 'Login succeeded, but session bootstrap failed. Please retry.',
        };
      }
      return { ok: true };
    } catch {
      clearAuth();
      return {
        ok: false,
        error: 'Authentication service is unreachable. Verify auth sidecar availability and network access, then retry.',
      };
    } finally {
      loading.value = false;
    }
  }

  function buildDetailedLoginError(status?: number, backendError?: string): string {
    if (backendError && backendError.trim()) {
      if (/https transport/i.test(backendError)) {
        return `Login blocked by security policy: ${backendError}. This usually means the auth service sees the request as non-HTTPS. In local dev, ensure TLS termination/proxy forwarding is configured correctly.`;
      }
      return `Login failed (${status ?? 'unknown status'}): ${backendError}`;
    }

    if (status === 401) {
      return 'Login failed (401 Unauthorized): username or password is incorrect.';
    }
    if (status === 400) {
      return 'Login failed (400 Bad Request): request payload is invalid or rejected by auth policy.';
    }
    if (status === 502) {
      return 'Login failed (502 Bad Gateway): auth service could not reach the backend authentication endpoint.';
    }

    return `Login failed${status ? ` (HTTP ${status})` : ''}: no additional error details were returned by the authentication service.`;
  }

  /*
  async function signup(name: string, email: string, password: string): Promise<boolean> {
    loading.value = true;
    try {
      await new Promise(resolve => setTimeout(resolve, 500));
      isAuthenticated.value = true;
      user.value = { name, email, role: 'maintainer' };
      return true;
    } catch {
      return false;
    } finally {
      loading.value = false;
    }
  }*/

  async function changePassword(currentPassword: string, newPassword: string): Promise<{ ok: boolean; error?: string }> {
    loading.value = true;
    try {
      const response = await fetch(`${AUTH_SERVICE_URL}/api/auth/change-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ currentPassword, newPassword }),
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) {
        return { ok: false, error: payload.error || 'Password change failed' };
      }
      return { ok: true };
    } catch {
      return { ok: false, error: 'Auth service unavailable' };
    } finally {
      loading.value = false;
    }
  }

  return {
    isAuthenticated,
    user,
    accessToken,
    loading,
    isLoggedIn,
    currentUser,
    isAdmin,
    isMaintainer,
    fetchFromAuthService,
    initFromSSOCallback,
    loginWithSSO,
    logout,
    getBearerToken,
    clearAuth,
    login,
    signup,
    changePassword,
  };
});
