/// <reference types="vite/client" />
/// <reference types="vite-plugin-pwa/client" />

interface ImportMetaEnv {
  /** Optional absolute API origin, for example when the app is wrapped as a native shell. Empty means same origin. */
  readonly VITE_API_BASE_URL?: string;
}
