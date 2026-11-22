import { expect, test } from '@playwright/test';
import { register, uniqueEmail } from '../helpers/app';

test.describe('installable app', () => {
  test('the web manifest describes an installable standalone app', async ({ request }) => {
    const res = await request.get('/manifest.webmanifest');
    expect(res.ok()).toBeTruthy();
    const manifest = await res.json();
    expect(manifest.name).toBe('PortfolioIQ');
    expect(manifest.display).toBe('standalone');
    expect(manifest.start_url).toBe('/');
    const sizes = manifest.icons.map((i: { sizes: string }) => i.sizes);
    expect(sizes).toContain('192x192');
    expect(sizes).toContain('512x512');
    expect(manifest.icons.some((i: { purpose?: string }) => i.purpose === 'maskable')).toBeTruthy();
    for (const icon of manifest.icons as Array<{ src: string }>) {
      const image = await request.get(icon.src);
      expect(image.ok(), icon.src).toBeTruthy();
      expect(image.headers()['content-type']).toContain('image/png');
    }
  });

  test('a service worker takes control so the app shell works offline', async ({ page }) => {
    await page.goto('/login');
    await page.waitForFunction(async () => (await navigator.serviceWorker.getRegistrations()).length > 0, null, { timeout: 20_000 });
  });

  test('the app is served with strict security headers and the API is proxied', async ({ request }) => {
    const res = await request.get('/');
    const headers = res.headers();
    expect(headers['content-security-policy']).toContain("default-src 'self'");
    expect(headers['x-content-type-options']).toBe('nosniff');
    expect(headers['x-frame-options']).toBe('DENY');
    expect((await request.get('/api/market/status')).status()).toBe(401); // reached the backend, which wants a token
    expect((await request.get('/api/market/status')).headers()['x-request-id']).toBeTruthy();
  });
});

test.describe('responsive layout', () => {
  test('phones get a bottom tab bar and computers get a sidebar', async ({ page, isMobile }) => {
    await register(page, uniqueEmail());
    const bottom = page.getByRole('navigation', { name: 'Primary' });
    const sidebar = page.getByRole('navigation', { name: 'Main' });
    if (isMobile) {
      await expect(bottom).toBeVisible();
      await expect(sidebar).toBeHidden();
      await bottom.getByRole('link', { name: 'Portfolios' }).click();
    } else {
      await expect(sidebar).toBeVisible();
      await expect(bottom).toBeHidden();
      await sidebar.getByRole('link', { name: 'Portfolios' }).click();
    }
    await expect(page).toHaveURL(/\/portfolios$/);
    await expect(page.getByRole('heading', { name: 'Portfolios' })).toBeVisible();
  });

  test('going offline shows a banner and coming back removes it', async ({ page, context }) => {
    await register(page, uniqueEmail());
    await context.setOffline(true);
    await expect(page.getByText('You are offline')).toBeVisible();
    await context.setOffline(false);
    await expect(page.getByText('You are offline')).toBeHidden();
  });
});
