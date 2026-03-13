import { test, expect } from '@playwright/test';

test.describe('Article Generation Flow', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('homepage has correct title', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Welcome to AI Content Generator' })).toBeVisible();
  });

  test('Sign In button redirects to login', async ({ page }) => {
    await page.getByRole('main').getByRole('link', { name: 'Sign In' }).click();
    await expect(page).toHaveURL(/.*\/login/);
  });
});
