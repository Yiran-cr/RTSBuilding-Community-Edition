import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 报告数据位于 docs/reports/*.json（位于本项目目录的上一级）
// 通过 glob 在构建时内联，避免运行时 fetch（file:// 打开也可用）
export default defineConfig({
  plugins: [vue()],
  base: './',
  build: {
    outDir: '../dist',
    emptyOutDir: true
  }
})
