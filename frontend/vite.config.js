import { defineConfig } from 'vite';

export default defineConfig({
  esbuild: { jsx: 'automatic', jsxImportSource: 'react' },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/face-api': {
        target: 'http://127.0.0.1:8001',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/face-api/, ''),
      },
    },
  },
  build: { outDir: 'dist' },
});
