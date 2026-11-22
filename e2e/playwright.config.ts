import { defineConfig, devices } from '@playwright/test';

// Runs against a live stack: docker compose -f docker-compose.yml -f docker-compose.override.yml -f docker-compose.e2e.yml up -d --build --wait
export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1, // tests share one database; each uses its own unique accounts
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop-chrome', use: { ...devices['Desktop Chrome'] } },
    // The phone profile runs the shell, sign-in and PWA checks; the long investing journey runs on desktop.
    { name: 'mobile-chrome', use: { ...devices['Pixel 7'] }, testIgnore: /investing/ },
  ],
});
