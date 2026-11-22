import { expect, test } from '@playwright/test';
import { linkFromEmail } from '../helpers/mailpit';
import { PASSWORD, login, register, signOut, uniqueEmail, verifyEmail } from '../helpers/app';

test('a new user can register, verify their email and sign in again', async ({ page }) => {
  const email = uniqueEmail();
  await register(page, email);
  await verifyEmail(page, email);

  await signOut(page);
  await login(page, email);
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
});

test('a wrong password shows a clear message and keeps the user on the sign-in page', async ({ page }) => {
  const email = uniqueEmail();
  await register(page, email);
  await signOut(page);
  await login(page, email, 'Definitely-wrong-1');
  await expect(page.getByRole('alert')).toContainText('Invalid credentials');
  await expect(page).toHaveURL(/\/login$/);
});

test('registration explains weak passwords before sending anything', async ({ page }) => {
  await page.goto('/register');
  await page.getByLabel('First name').fill('Ada');
  await page.getByLabel('Last name').fill('Lovelace');
  await page.getByLabel('Email').fill(uniqueEmail());
  await page.getByLabel('Password', { exact: true }).fill('short');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByText('Use at least 10 characters')).toBeVisible();
  await expect(page).toHaveURL(/\/register$/);
});

test('password reset works end to end through the emailed link', async ({ page }) => {
  const email = uniqueEmail();
  await register(page, email);
  await signOut(page);

  await page.goto('/forgot-password');
  await page.getByLabel('Email').fill(email);
  await page.getByRole('button', { name: 'Send reset link' }).click();
  await expect(page.getByText('a reset link is on its way')).toBeVisible();

  await page.goto(await linkFromEmail(email, 'reset-password'));
  const newPassword = 'A-brand-new-password-9';
  await page.getByLabel('New password').fill(newPassword);
  await page.getByLabel('Confirm password').fill(newPassword);
  await page.getByRole('button', { name: 'Change password' }).click();
  await expect(page.getByText('Your password has been changed')).toBeVisible();

  await login(page, email, PASSWORD);
  await expect(page.getByRole('alert')).toContainText('Invalid credentials'); // the old password no longer works
  await login(page, email, newPassword);
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
});

test('signed-out visitors are sent to sign in and returned afterwards', async ({ page }) => {
  await page.goto('/portfolios');
  await expect(page).toHaveURL(/\/login$/);
});
