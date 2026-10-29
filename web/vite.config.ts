import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // The API runs on :8080 (backend, dev profile); same origin for the browser.
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // the screen tests type whole forms with user-event; 5 s is too tight under a full parallel run
    testTimeout: 15_000,
  },
})
