import { test, expect } from '@playwright/test';

test.describe('Admin Users CRUD', () => {
  test('admin can create, update and delete users', async ({ page }) => {
    const users = [
      { id: 1, username: 'admin', role: 'admin' },
    ];
    let nextId = 2;

    await page.route('**/api/auth/login', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          ok: true,
          user: { id: 1, name: 'admin', email: 'admin@local', role: 'admin' },
        }),
      });
    });

    await page.route('**/api/admin/users**', async (route) => {
      const request = route.request();
      const url = new URL(request.url());
      const method = request.method();
      if (method === 'GET') {
        const username = (url.searchParams.get('username') || '').toLowerCase();
        const pageNum = Number(url.searchParams.get('page') || '0');
        const size = Number(url.searchParams.get('size') || '10');
        const filtered = users.filter(user => user.username.toLowerCase().includes(username));
        const start = pageNum * size;
        const end = start + size;
        const items = filtered.slice(start, end);
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            items,
            page: pageNum,
            size,
            totalElements: filtered.length,
            totalPages: Math.max(Math.ceil(filtered.length / size), 1),
          }),
        });
        return;
      }

      if (method === 'POST') {
        const payload = request.postDataJSON() as { username: string; role: string };
        const created = { id: nextId++, username: payload.username, role: payload.role };
        users.push(created);
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(created),
        });
        return;
      }

      if (method === 'PUT') {
        const id = Number(url.pathname.split('/').pop());
        const payload = request.postDataJSON() as { username: string; role: string };
        const target = users.find(user => user.id === id);
        if (!target) {
          await route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ error: 'Not found' }) });
          return;
        }
        target.username = payload.username;
        target.role = payload.role;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(target),
        });
        return;
      }

      if (method === 'DELETE') {
        const id = Number(url.pathname.split('/').pop());
        const idx = users.findIndex(user => user.id === id);
        if (idx >= 0) {
          users.splice(idx, 1);
        }
        await route.fulfill({ status: 204, body: '' });
        return;
      }

      await route.continue();
    });

    await page.goto('/login');
    await page.getByLabel('Username').fill('admin');
    await page.getByLabel('Password').fill('admin');
    await page.getByRole('button', { name: 'Sign In' }).click();
    await expect(page).toHaveURL(/\/$/);

    await page.getByRole('link', { name: 'Users' }).click();
    await expect(page).toHaveURL(/\/admin\/users/);

    await page.getByRole('button', { name: 'Create User' }).click();
    await page.getByLabel('Initial username').fill('qa_user');
    await page.getByLabel('Initial password').fill('qa_user_123');
    await page.getByRole('button', { name: 'Create' }).click();

    await page.getByLabel('Search by username').fill('qa_user');
    await page.getByRole('button', { name: 'Search' }).click();
    await expect(page.getByText('qa_user')).toBeVisible();

    await page.getByRole('button', { name: 'Edit' }).first().click();
    await page.getByLabel('Initial username').fill('qa_user_updated');
    await page.getByRole('button', { name: 'Save' }).click();

    await page.getByLabel('Search by username').fill('qa_user_updated');
    await page.getByRole('button', { name: 'Search' }).click();
    await expect(page.getByText('qa_user_updated')).toBeVisible();

    await page.getByRole('button', { name: 'Delete' }).first().click();
    await page.getByRole('button', { name: 'OK' }).click();
    await expect(page.getByText('qa_user_updated')).toHaveCount(0);
  });
});
