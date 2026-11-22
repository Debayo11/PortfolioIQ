import { expect, test } from '@playwright/test';
import { ensureAdmin, register, uniqueEmail, uniqueSymbol } from '../helpers/app';

test('an administrator adds an asset and an investor records a purchase and sees it valued', async ({ page, browser }) => {
  const symbol = uniqueSymbol();

  // 1. The administrator adds the asset to the shared catalogue.
  await ensureAdmin(page);
  await page.goto('/market');
  await page.getByRole('button', { name: 'Add asset' }).click();
  const assetDialog = page.getByRole('dialog', { name: 'Add asset' });
  await assetDialog.getByLabel('Symbol').fill(symbol);
  await assetDialog.getByLabel('Name', { exact: true }).fill('End To End Corp');
  await assetDialog.getByRole('button', { name: 'Add asset' }).click();
  await page.getByLabel('Search assets').fill(symbol);
  await expect(page.getByText('End To End Corp')).toBeVisible();

  // 2. A separate investor creates a portfolio and records a buy.
  const investor = await browser.newPage();
  await register(investor, uniqueEmail('investor'));
  await investor.goto('/portfolios');
  await investor.getByRole('button', { name: 'New portfolio' }).click();
  const portfolioDialog = investor.getByRole('dialog', { name: 'New portfolio' });
  await portfolioDialog.getByLabel('Name').fill('E2E Portfolio');
  await portfolioDialog.getByRole('button', { name: 'Create portfolio' }).click();
  await expect(investor.getByRole('heading', { name: 'E2E Portfolio' })).toBeVisible();

  await investor.getByRole('button', { name: 'Add transaction' }).click();
  const txDialog = investor.getByRole('dialog', { name: 'Add transaction' });
  await txDialog.getByLabel('Asset', { exact: true }).fill(symbol);
  await txDialog.getByRole('button', { name: new RegExp(symbol) }).click();
  await txDialog.getByLabel('Quantity').fill('10');
  await txDialog.getByLabel('Price per unit').fill('100');
  await txDialog.getByRole('button', { name: 'Record transaction' }).click();

  // 3. The holding shows up, valued with clearly labelled simulated prices.
  await expect(investor.getByRole('link', { name: symbol }).first()).toBeVisible();
  await expect(investor.getByText('Simulated data').first()).toBeVisible();

  // 4. The dashboard and analytics reflect it, and state they describe the past only.
  await investor.goto('/');
  await expect(investor.getByText('Total portfolio value')).toBeVisible();
  await expect(investor.getByText('Active investments')).toBeVisible();
  await investor.goto('/analytics');
  await expect(investor.getByText('Time-weighted return').first()).toBeVisible();
  await expect(investor.getByText(/not projections/)).toBeVisible();
  await investor.close();
});

test('an administrator can find a user and sees the administration area', async ({ page, browser }) => {
  const email = uniqueEmail('findable');
  const other = await browser.newPage();
  await register(other, email);
  await other.close();

  await ensureAdmin(page);
  await page.goto('/admin/users');
  await expect(page.getByRole('heading', { name: 'Administration' })).toBeVisible();
  await page.getByLabel('Search users').fill(email);
  await page.getByText(email).click();
  const dialog = page.getByRole('dialog', { name: 'Manage user' });
  await expect(dialog.getByText(email)).toBeVisible();
  await expect(dialog.getByText('Investor')).toBeVisible();
});

test('an ordinary user cannot open the administration area', async ({ page }) => {
  await register(page, uniqueEmail());
  await page.goto('/admin');
  await expect(page.getByText('Not authorised')).toBeVisible();
});
