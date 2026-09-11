import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Vite 配置
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // 开发环境代理：前端请求 /api 自动转发到后端 8080
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 上传的视频/封面文件：/upload/xxx 转发到后端静态资源
      // 注意用 '/upload/'（带斜杠）而不是 '/upload'，否则会拦截前端路由 /upload（投稿页）
      '/upload/': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // WebSocket：私信实时推送，/ws 转发到后端（ws:true 启用协议升级）
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true
      }
    }
  }
})
