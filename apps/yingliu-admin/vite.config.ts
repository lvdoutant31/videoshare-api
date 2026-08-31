import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  base: '/admin/',
  plugins: [vue()],
  server: { proxy: { '/api': 'http://localhost:8080' } },
  test: { environment: 'jsdom', globals: true }
});
