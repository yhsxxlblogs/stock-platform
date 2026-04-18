# 多阶段构建：构建阶段
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# 安装必要的构建工具
RUN apk add --no-cache freetype fontconfig ttf-dejavu

# 复制源代码
COPY . .

# 构建项目
RUN mvn clean package -DskipTests -B

# 生产阶段
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# 安装必要的字体库和 wget
RUN apk add --no-cache freetype fontconfig ttf-dejavu wget

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
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -q -O - http://localhost:9090/api/stocks/public/list > /dev/null 2>&1 || exit 1

# 启动命令
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8", "-jar", "app.jar"]
