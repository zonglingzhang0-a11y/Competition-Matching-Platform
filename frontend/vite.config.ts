import { existsSync, readdirSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// Element Plus 每个组件的样式入口（element-plus/es/components/xxx/style/css），开发时预打包用。
// optimizeDeps.include 的通配符匹配的是文件名（css.mjs），和按需引入写的路径对不上，所以这里一个个列出来
const elDir = fileURLToPath(new URL('./node_modules/element-plus/es/components', import.meta.url))
const elStyles = existsSync(elDir)
  ? readdirSync(elDir)
      .filter((name) => existsSync(`${elDir}/${name}/style/css.mjs`))
      .map((name) => `element-plus/es/components/${name}/style/css`)
  : []

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
    // Element Plus 按需引入：模板里用到哪个 el-xxx 才打包哪个，连同它的样式（首屏不再加载整个组件库）
    Components({
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
      dts: 'src/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  // 开发时启动就把 Element Plus 的组件和样式预打包好。否则第一次打开用到新组件的页面时，
  // Vite 要重新预打包并刷新页面，偶尔会看到"加载模块失败"、需要手动刷新
  optimizeDeps: {
    include: ['element-plus/es', ...elStyles],
  },
  build: {
    // 唯一超过 500 KB 的是管理端数据看板的 ECharts，只有管理员打开看板时才加载，不影响首屏
    chunkSizeWarningLimit: 560,
  },
  server: {
    port: 5173,
    // 开发时把 /api 转发给后端，前后端看起来是同一个域名，不用处理跨域。
    // ws: true 连同站内信推送的 WebSocket（/api/ws/notifications）一起转发。
    // 想连别的后端（比如测试库上的那个）就设环境变量 API_TARGET
    proxy: {
      '/api': { target: process.env.API_TARGET || 'http://localhost:8090', ws: true },
    },
  },
})
