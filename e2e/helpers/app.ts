import { expect, type Page } from '@playwright/test';
import { linkFromEmail } from './mailpit';

export const PASSWORD = 'E2e-password-12345';
export const ADMIN_EMAIL = 'admin@e2e.test'; // matches BOOTSTRAP_ADMIN_EMAIL in docker-compose.e2e.yml

export function uniqueEmail(prefix = 'user'): string {
  return `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}@e2e.test`;
}

export function uniqueSymbol(): string {
  return `E2E${Date.now().toString(36).toUpperCase()}${Math.random().toString(36).slice(2, 4).toUpperCase()}`;
}

export async function register(page: Page, email: string, first = 'Ada', last = 'Lovelace'): Promise<void> {
  await page.goto('/register');
  await page.getByLabel('First name').fill(first);
  await page.getByLabel('Last name').fill(last);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
}

export async function login(page: Page, email: string, password = PASSWORD): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

/** Signs out from the Settings screen, which exists on phones and computers alike. */
export async function signOut(page: Page): Promise<void> {
  await page.goto('/settings');
  await page.getByRole('button', { name: 'Sign out' }).first().click();
  await expect(page).toHaveURL(/\/login$/);
}

/** Opens the verification link from the real email and checks the confirmation. */
export async function verifyEmail(page: Page, email: string): Promise<void> {
  const link = await linkFromEmail(email, 'verify-email');
  await page.goto(link);
  await expect(page.getByText('Your email address is verified.')).toBeVisible();
}

/**
 * Signs in as the bootstrap administrator, creating and verifying the account first if this is a fresh stack.
 * The role is granted at verification, which is why the page is reloaded afterwards.
 */
export async function ensureAdmin(page: Page): Promise<void> {
  await login(page, ADMIN_EMAIL);
  const signedIn = await page.getByRole('heading', { name: 'Dashboard' }).waitFor({ timeout: 5_000 }).then(() => true, () => false);
  if (!signedIn) {
    await register(page, ADMIN_EMAIL, 'Admin', 'User');
    await verifyEmail(page, ADMIN_EMAIL);
  }
  await page.goto('/');
  await expect(page.getByRole('link', { name: 'Administration' })).toBeVisible();
}
