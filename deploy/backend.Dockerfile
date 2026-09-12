# =========================================================
# 视频社区后端镜像（多阶段构建）：Spring Boot 3.2 / Java 17 + FFmpeg
#   阶段1：Maven 打包出可执行 jar
#   阶段2：运行镜像（含 FFmpeg——投稿自动抽封面是硬依赖）
# 构建：项目根目录执行 docker compose up -d --build 即可，无需手动 mvn package
# =========================================================

# ---------- 阶段1：编译打包 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# 先拷 pom 单独拉依赖（利用 Docker 层缓存，改代码不重下依赖）
COPY backend/pom.xml .
RUN mvn -q -e dependency:go-offline || true
COPY backend/src ./src
RUN mvn -q package -DskipTests

# ---------- 阶段2：运行 ----------
FROM eclipse-temurin:17-jre

# 安装 FFmpeg + 时区数据，并设置上海时区（避免容器内时间差 8 小时）
# 先替换 apt 源为阿里云镜像（境外源慢且不稳，曾导致多次 Ign 重试耗时数分钟）
RUN sed -i 's@//.*archive.ubuntu.com@//mirrors.aliyun.com@g; s@//security.ubuntu.com@//mirrors.aliyun.com@g' /etc/apt/sources.list.d/ubuntu.sources 2>/dev/null || true; \
    sed -i 's@//.*archive.ubuntu.com@//mirrors.aliyun.com@g; s@//security.ubuntu.com@//mirrors.aliyun.com@g' /etc/apt/sources.list 2>/dev/null || true; \
    apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg tzdata \
    && rm -rf /var/lib/apt/lists/*

ENV TZ=Asia/Shanghai

WORKDIR /app

# 从构建阶段拷贝可执行 jar
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
