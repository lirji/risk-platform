import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const apiTarget = loadEnv(mode, process.cwd(), '').VITE_DEV_API_TARGET || 'http://localhost:8083'
  const backendProxy = { target: apiTarget, changeOrigin: false, xfwd: true }
  return {
    plugins: [vue()],
    server: {
      port: 5173,
      proxy: {
        '/api': backendProxy,
        '/actuator': backendProxy,
      },
    },
  }
})
