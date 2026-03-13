import { test, expect } from '@playwright/test';

test.describe('Authentication Flow', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('displays the homepage', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Welcome to AI Content Generator' })).toBeVisible();
    await expect(page.getByRole('main').getByRole('link', { name: 'Sign In' })).toBeVisible();
  });

  test('navigates to sign in page', async ({ page }) => {
    await page.getByRole('main').getByRole('link', { name: 'Sign In' }).click();
    await expect(page).toHaveURL(/.*\/login/);
  });

  test('navigates to sign up page', async ({ page }) => {
    await page.goto('/signup');
    await expect(page).toHaveURL(/.*\/signup/);
  });

  test('shows unauthenticated message on change password page', async ({ page }) => {
    await page.goto('/change-password');
    await expect(page).toHaveURL(/.*\/change-password/);
    await expect(page.getByRole('heading', { name: 'Change Password' })).toBeVisible();
    await expect(page.getByText('Please sign in to change your password.')).toBeVisible();
  });

  test('shows unauthenticated message on admin users page', async ({ page }) => {
    await page.goto('/admin/users');
    await expect(page).toHaveURL(/.*\/admin\/users/);
    await expect(page.getByRole('heading', { name: 'User Management' })).toBeVisible();
    await expect(page.getByText('Please sign in to access admin user management.')).toBeVisible();
  });
});
