import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

/**
 * Vite configuration.
 *
 * - `npm run dev` serves the UI on http://localhost:5173 and proxies `/api` to the Spring Boot
 *   backend (http://localhost:8080 by default, override with TSAREVNA_API_URL), so cookies and
 *   CSRF work exactly as in production (same origin).
 * - `npm run build` writes the bundle into the backend's static resources, so the packaged jar
 *   serves the UI itself.
 */
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.TSAREVNA_API_URL ?? 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
    sourcemap: false,
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          charts: ['recharts'],
          editor: ['@uiw/react-codemirror', '@codemirror/lang-json'],
        },
      },
    },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
});
