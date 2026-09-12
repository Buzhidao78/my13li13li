# =========================================================
# 视频社区前端镜像：两阶段构建
# 阶段1：node 构建出 dist 静态文件
# 阶段2：nginx 托管静态页 + 内部反代后端（本地直连 8090 可用；
#        云服务器上 /api、/upload、/ws 由宿主机 nginx 先拦截，本反代不生效，无冲突）
# =========================================================
FROM node:20-alpine AS build
WORKDIR /app
COPY frontend/package*.json ./
# 使用国内镜像源 + 关闭 audit/fund，避免境外源超时导致 npm 进程崩溃（Exit handler never called!）
RUN npm install --registry=https://registry.npmmirror.com --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
# 覆盖默认站点配置：SPA history 回退 + 反代后端
COPY deploy/frontend.nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
