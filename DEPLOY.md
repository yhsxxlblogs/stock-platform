# Stock Platform 部署指南

## 环境要求

- Docker 20.10+
- Docker Compose 2.0+
- 服务器内存：建议 2GB+

## 快速部署

### 1. 克隆项目

```bash
git clone https://github.com/yhsxxlblogs/stock-platform.git
cd stock-platform
```

### 2. 构建镜像

```bash
# 构建后端镜像
cd stock-backend
docker build -t stock-platform-backend:latest .
cd ..

# 构建前端镜像
cd stock-frontend
docker build -t stock-platform-frontend:latest .
cd ..
```

### 3. 启动服务

```bash
# 使用 docker-compose 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

### 4. 配置 Nginx（生产环境）

将 `nginx/bt-nginx.conf` 复制到宝塔面板的网站配置中，或直接使用以下配置：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # WebSocket 代理
    location /api/ws {
        proxy_pass http://127.0.0.1:9090;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 86400s;
    }

    # API 代理
    location /api/ {
        proxy_pass http://127.0.0.1:9090/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # 前端代理
    location / {
        proxy_pass http://127.0.0.1:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_intercept_errors on;
        error_page 404 = /index.html;
    }
}
```

## 服务说明

| 服务 | 容器名 | 端口 | 说明 |
|------|--------|------|------|
| MySQL | stock-mysql | 3306 (内部) | 数据库 |
| Redis | stock-redis | 6379 (内部) | 缓存 |
| Backend | stock-backend | 127.0.0.1:9090 | 后端 API |
| Frontend | stock-frontend | 8080 | 前端 Nginx |

## 常用命令

```bash
# 查看日志
docker-compose logs -f [service-name]

# 重启服务
docker-compose restart [service-name]

# 停止所有服务
docker-compose down

# 停止并删除数据卷（谨慎使用）
docker-compose down -v

# 进入容器
docker exec -it [container-name] bash

# 查看容器状态
docker-compose ps
```

## 数据库迁移

如果需要导入外部 SQL 文件：

```bash
# 复制 SQL 文件到容器
docker cp your-data.sql stock-mysql:/tmp/

# 进入容器导入
docker exec -it stock-mysql bash
mysql -u root -p stock_platform < /tmp/your-data.sql
```

## 注意事项

1. **首次启动**：MySQL 初始化需要 30-60 秒，请耐心等待
2. **健康检查**：后端服务有 120 秒的启动缓冲期
3. **内存限制**：后端服务默认限制 1GB 内存
4. **数据持久化**：MySQL 和 Redis 数据使用 Docker Volume 持久化
