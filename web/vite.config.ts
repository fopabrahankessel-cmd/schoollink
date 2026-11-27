import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // The API runs on :8080 (backend, dev profile); same origin for the browser. API_PROXY points a
    // second dev server at another backend.
    proxy: { '/api': process.env.API_PROXY ?? 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // the screen tests type whole forms with user-event; 5 s is too tight under a full parallel run
    testTimeout: 15_000,
    // One worker per core starved the CPU: screens that take 4 s alone timed out at random in the
    // full suite. Half the cores runs the suite just as fast (~2 min) and passes every time.
    maxWorkers: '50%',
  },
})
