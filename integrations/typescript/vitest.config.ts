import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    // Building the service images and exploring takes minutes on a cold start
    testTimeout: 300_000,
    hookTimeout: 600_000,
  },
});
