import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Vite 配置：Vue3 插件 + Vitest 测试环境（jsdom）
// 前端 mock 支线：无需代理到真实后端，mock 数据在 src/mock 内完成
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    open: false
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets'
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/__tests__/**/*.test.js']
  }
})
