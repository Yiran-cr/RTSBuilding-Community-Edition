import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 文档站构建配置：vue 插件 + 开发服务器端口
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    open: false
  }
})
