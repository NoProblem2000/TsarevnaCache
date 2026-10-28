/**
 * Tailwind configuration with the GitKraken inspired dark palette.
 *
 * Usage: `bg-gk-bg`, `bg-gk-panel`, `text-gk-cyan`, `border-gk-border`, `font-mono`, ...
 *
 * @type {import('tailwindcss').Config}
 */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        gk: {
          bg: '#14171A',
          panel: '#1C2128',
          card: '#22272E',
          hover: '#2A313A',
          border: '#2D333B',
          text: '#E6EDF3',
          muted: '#8B949E',
          subtle: '#6E7681',
          cyan: '#00E5FF',
          purple: '#9B51E0',
          green: '#2ECC71',
          warning: '#F39C12',
          danger: '#E74C3C',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'Segoe UI', 'sans-serif'],
        mono: ['"JetBrains Mono"', '"Fira Code"', 'Consolas', 'monospace'],
      },
      boxShadow: {
        glow: '0 0 0 1px rgba(0, 229, 255, 0.35), 0 0 18px rgba(0, 229, 255, 0.15)',
        panel: '0 8px 30px rgba(0, 0, 0, 0.45)',
      },
      keyframes: {
        'slide-in': {
          from: { transform: 'translateX(100%)' },
          to: { transform: 'translateX(0)' },
        },
        'fade-in': {
          from: { opacity: '0' },
          to: { opacity: '1' },
        },
      },
      animation: {
        'slide-in': 'slide-in 180ms ease-out',
        'fade-in': 'fade-in 150ms ease-out',
      },
    },
  },
  plugins: [],
};
