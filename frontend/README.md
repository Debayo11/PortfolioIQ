# PortfolioIQ app (React + TypeScript + Tailwind + Recharts)

One codebase that runs on phones, tablets and computers. It is a Progressive Web App: people open it in a
browser and can **install it** so it launches in its own window with its own icon.

- **Phones and tablets:** bottom tab bar, large touch targets, safe-area aware (notches, home indicator)
- **Computers:** sidebar navigation, wider layouts, tables instead of cards
- **Installable:** Chrome and Edge (Windows, macOS, Linux, ChromeOS, Android) show an Install button; on iPhone and iPad use Share, then Add to Home Screen
- **Offline behaviour:** the app shell loads offline; financial data is never served from a cache, and an offline banner explains why data is stale

## Administration

Users with the Administrator role get an Administration area (sidebar on computers, a card in Settings on phones):
overview statistics, user and role management, activity log, system log and API usage, and market data controls.
See the repository README for creating the first administrator.

## Run it

```bash
cd frontend
cp .env.example .env        # optional; the defaults work with the backend on port 8080
npm install
npm run dev                 # http://localhost:5173 (proxies /api to the backend)
```

With Docker Compose from the repository root, the app is served at http://localhost:3000.

```bash
npm run typecheck           # strict TypeScript
npm test                    # unit tests for formatting, session handling and validation
npm run build               # production build in dist/
```

Run `npm install` once and commit the generated `package-lock.json` so builds are reproducible.

## Installing on a phone needs HTTPS

Browsers only allow installation and service workers on secure origins. `localhost` counts, so installing on the
computer you develop on works immediately. A phone opening `http://192.168.x.x:3000` will use the site but cannot
install it. To install on a phone, serve the app over HTTPS (for example behind Caddy or another TLS proxy, or a
tunnel such as Cloudflare Tunnel) and set `FRONTEND_URL` for the backend to that address.

## Structure

```
src/
├── api/          typed client, endpoint functions, response types
├── auth/         sign-in state and route guard
├── components/   ui (design system), layout (shell, nav), charts, dialogs
├── lib/          formatting, validation, session storage, install helper
└── pages/        one file per screen
```

## Design notes

- Money and percentages are formatted for display only; all financial calculation happens on the server.
- Forms validate with the same rules as the server for instant feedback, and still show the server's message if it refuses.
- Data labelled as simulated by the API is badged everywhere it appears.
- The JWT is kept in local storage and discarded when it expires (60 minutes by default, set by the backend). There are
  no refresh tokens yet, so people sign in again after expiry. The nginx config sends a strict Content-Security-Policy.

## Wrapping as a store app or desktop installer (optional, not included)

The same build can be packaged natively: **Capacitor** for the iOS App Store and Google Play, **Tauri** or Electron
for Windows, macOS and Linux installers. Those need platform toolchains (Xcode, Android Studio) and signing
accounts, so they are not part of this repository. Native shells call the API directly, so set
`VITE_API_BASE_URL` at build time and list the shell's origin in the backend's `CORS_ALLOWED_ORIGINS`.
