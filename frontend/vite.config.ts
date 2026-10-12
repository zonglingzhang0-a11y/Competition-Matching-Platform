import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // 开发时把 /api 转发给后端，前后端看起来是同一个域名，不用处理跨域。
    // 想连别的后端（比如测试库上的那个）就设环境变量 API_TARGET
    proxy: {
      '/api': process.env.API_TARGET || 'http://localhost:8090',
    },
  },
})
