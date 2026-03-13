import { createRouter, createWebHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('layouts/MainLayout.vue'),
    children: [
      { path: '', component: () => import('pages/IndexPage.vue') },
      {
        path: 'login',
        component: () => import('pages/SignInPage.vue'),
        meta: { public: true },
      },
      {
        path: 'signup',
        component: () => import('pages/SignUpPage.vue'),
        meta: { public: true },
      },
      {
        path: 'change-password',
        component: () => import('pages/ChangePasswordPage.vue'),
      },
      {
        path: 'settings',
        component: () => import('pages/SettingsPage.vue'),
      },
      {
        path: 'admin/users',
        component: () => import('pages/AdminUsersPage.vue'),
        meta: { roles: ['admin'] },
      },
      {
        path: 'content/articles',
        component: () => import('pages/ArticlesWorkspacePage.vue'),
        meta: { roles: ['admin', 'maintainer'] },
      },
      {
        path: 'content/articles/preview/:id',
        component: () => import('pages/ArticlePreviewPage.vue'),
        meta: { roles: ['admin', 'maintainer'] },
      },
      {
        path: 'content/generate',
        component: () => import('pages/ArticleGenerationPage.vue'),
        meta: { roles: ['admin', 'maintainer'] },
      },
      {
        path: 'content/generation-jobs',
        component: () => import('pages/ArticleGenerationJobsPage.vue'),
      },
      {
        path: 'admin/running-kie-processes',
        component: () => import('pages/RunningKieProcessesPage.vue'),
        meta: { roles: ['admin'] },
      },
    ],
  },
  {
    path: '/:catchAll(.*)*',
    component: () => import('pages/ErrorNotFound.vue'),
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

let authBootstrapPromise: Promise<boolean> | null = null;

router.beforeEach(async (to) => {
  if (to.meta.public) {
    return true;
  }

  const auth = useAuthStore();
  if (auth.isLoggedIn) {
    const roles = to.meta.roles as string[] | undefined;
    if (!roles || roles.length === 0) {
      return true;
    }
    const currentRole = auth.currentUser?.role;
    if (currentRole && roles.includes(currentRole)) {
      return true;
    }
    return '/';
  }

  if (!authBootstrapPromise) {
    authBootstrapPromise = auth.fetchFromAuthService();
  }
  const restored = await authBootstrapPromise;
  authBootstrapPromise = null;

  if (!restored) {
    return '/login';
  }

  const roles = to.meta.roles as string[] | undefined;
  if (!roles || roles.length === 0) {
    return true;
  }

  const currentRole = auth.currentUser?.role;
  if (currentRole && roles.includes(currentRole)) {
    return true;
  }

  return '/';
});

export default router;
