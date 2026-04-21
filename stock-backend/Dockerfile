# 多阶段构建：构建阶段
# 使用南京大学开源镜像站加速（适用于中国大陆）
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# 安装必要的构建工具
RUN apk add --no-cache freetype fontconfig ttf-dejavu

# 复制源代码
COPY . .

# 构建项目（使用国内镜像加速）
RUN echo '<?xml version="1.0" encoding="UTF-8"?><settings><mirrors><mirror><id>aliyun</id><mirrorOf>*</mirrorOf><url>https://maven.aliyun.com/repository/public</url></mirror></mirrors></settings>' > /usr/share/maven/conf/settings.xml || true
RUN mvn clean package -DskipTests -B

# 生产阶段 - 使用更轻量的JRE
# 使用阿里云镜像加速
FROM openjdk:17-jre-slim
WORKDIR /app

# 安装必要的字体库和 wget
RUN apt-get update && apt-get install -y --no-install-recommends \
    fonts-dejavu-core \
    fontconfig \
    wget \
    && rm -rf /var/lib/apt/lists/*

# 设置时区和编码
ENV TZ=Asia/Shanghai
ENV LANG=zh_CN.UTF-8
ENV LANGUAGE=zh_CN:zh
ENV LC_ALL=zh_CN.UTF-8

# 创建上传目录
RUN mkdir -p /app/uploads/avatars && chmod -R 755 /app/uploads

# 复制构建好的 jar 包
COPY --from=build /app/target/*.jar app.jar

# 暴露端口
EXPOSE 9090

# 健康检查
HEALTHCHECK --interval=60s --timeout=3s --start-period=120s --retries=3 \
    CMD wget -q -O - http://localhost:9090/api/stocks/public/list > /dev/null 2>&1 || exit 1

# 启动命令 - 使用优化的JVM参数
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
