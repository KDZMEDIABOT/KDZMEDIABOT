import { defineConfig } from 'vitest/config';
import { resolve } from 'path';

export default defineConfig({
  test: {
    globals: true,
    environment: 'happy-dom',
    include: ['tests/unit/**/*.spec.ts'],
    coverage: {
      reporter: ['text', 'json', 'html'],
      exclude: ['node_modules/', 'tests/']
    }
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, './src'),
      'src/': resolve(__dirname, './src/'),
      'components/': resolve(__dirname, './src/components/'),
      'stores/': resolve(__dirname, './src/stores/'),
      'boot/': resolve(__dirname, './src/boot/'),
    },
  },
});
