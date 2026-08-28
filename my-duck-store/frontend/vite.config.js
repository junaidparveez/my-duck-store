import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Forward every /api request to the Spring Boot backend during development.
    // In production the two are served from the same origin, so no proxy is needed.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
