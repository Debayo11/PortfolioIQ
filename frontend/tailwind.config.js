/** Premium fintech palette: deep navy and charcoal surfaces, emerald for gains, red for losses. */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: {
          950: '#070b14', 900: '#0b1220', 800: '#111a2e', 700: '#1a2540', 600: '#25324f',
          500: '#3a4a6b', 400: '#6b7a99', 300: '#94a3b8', 200: '#cbd5e1', 100: '#e2e8f0',
        },
        gain: '#34d399',
        loss: '#f87171',
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'Helvetica Neue', 'Arial', 'sans-serif'],
      },
      boxShadow: { card: '0 1px 0 0 rgba(255,255,255,0.04) inset, 0 8px 24px -12px rgba(0,0,0,0.6)' },
      keyframes: {
        rise: { '0%': { opacity: '0', transform: 'translateY(6px)' }, '100%': { opacity: '1', transform: 'translateY(0)' } },
        sheet: { '0%': { transform: 'translateY(24px)', opacity: '0' }, '100%': { transform: 'translateY(0)', opacity: '1' } },
      },
      animation: { rise: 'rise 0.25s ease-out both', sheet: 'sheet 0.22s ease-out both' },
    },
  },
  plugins: [],
};
