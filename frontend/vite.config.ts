import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

// In development the API is proxied so the browser sees one origin (no CORS needed).
// In production nginx does the same (see nginx.conf).
export default defineConfig({
  plugins: [
    react(),
    ...(process.env.VITEST ? [] : [VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'icons/apple-touch-icon.png'],
      manifest: {
        name: 'PortfolioIQ',
        short_name: 'PortfolioIQ',
        description: 'Investment and portfolio management',
        theme_color: '#070b14',
        background_color: '#070b14',
        display: 'standalone',
        orientation: 'any',
        start_url: '/',
        scope: '/',
        categories: ['finance', 'productivity'],
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: '/icons/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        // Only the app shell is cached. Financial data is never served from a cache by the service worker.
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api\//, /^\/swagger-ui/, /^\/v3\//, /^\/actuator\//],
        globPatterns: ['**/*.{js,css,html,svg,png,ico,woff2}'],
      },
    })]),
  ],
  test: { include: ['src/**/*.test.ts'], environment: 'node' },
  server: {
    port: 5173,
    proxy: { '/api': { target: process.env.VITE_DEV_API ?? 'http://localhost:8080', changeOrigin: true } },
  },
  build: { sourcemap: false, target: 'es2020' },
});
