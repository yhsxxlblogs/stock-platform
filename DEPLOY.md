# Stock Platform 部署指南

## 环境要求

- **服务器配置**: 2核2G内存（最低要求）
- **系统**: CentOS 7/8, Ubuntu 18.04/20.04/22.04
- **Docker**: 20.10+
- **Docker Compose**: 2.0+
- **磁盘空间**: 建议 20GB+

## 宝塔面板部署步骤

### 1. 安装 Docker 和 Docker Compose

在宝塔面板终端中执行：

```bash
# 安装 Docker
curl -fsSL https://get.docker.com | sh

# 安装 Docker Compose
pip3 install docker-compose

# 启动 Docker
systemctl start docker
systemctl enable docker
```

### 2. 克隆项目

```bash
cd /www/wwwroot
git clone https://github.com/yhsxxlblogs/stock-platform.git
cd stock-platform
```

### 3. 配置环境变量

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑 .env 文件
vi .env
```

修改以下配置：
```env
# MySQL 配置（必须修改密码）
MYSQL_ROOT_PASSWORD=你的安全密码
MYSQL_DATABASE=stock_platform

# JWT 密钥（必须修改，用于加密）
JWT_SECRET=你的随机密钥字符串

# 服务器域名
SERVER_DOMAIN=你的域名.com
```

### 4. 一键部署

```bash
# 添加执行权限
chmod +x deploy.sh

# 执行部署
./deploy.sh deploy
```

### 5. 配置宝塔 Nginx 反向代理

在宝塔面板中：

1. 创建网站，绑定你的域名
2. 在网站设置中选择「反向代理」
3. 添加反向代理：
   - 目标URL: `http://127.0.0.1:8080`
   - 发送域名: `$host`

或者手动编辑 Nginx 配置：

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

### 6. 配置 HTTPS（可选）

在宝塔面板中申请 SSL 证书并开启 HTTPS。

## 常用命令

```bash
# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f

# 查看后端日志
docker-compose logs -f backend

# 重启服务
./deploy.sh restart

# 停止服务
./deploy.sh stop

# 更新部署（拉取最新代码并重建）
./deploy.sh update

# 进入后端容器
docker exec -it stock-backend sh

# 进入 MySQL 容器
docker exec -it stock-mysql mysql -uroot -p
```

## 资源限制说明

针对 2核2G 服务器，Docker 容器资源限制如下：

| 服务 | CPU限制 | 内存限制 | 说明 |
|------|---------|----------|------|
| MySQL | 0.5核 | 512MB | 数据库服务 |
| Redis | 0.25核 | 128MB | 缓存服务 |
| Backend | 0.75核 | 640MB | Java后端（堆内存512MB） |
| Frontend | 0.25核 | 128MB | Nginx前端 |
| **总计** | **1.75核** | **1.4GB** | 预留600MB给系统 |

## 性能优化建议

1. **数据库优化**: MySQL 已配置连接池限制为10个连接
2. **Redis优化**: 限制最大内存128MB，使用LRU淘汰策略
3. **JVM优化**: 使用G1垃圾收集器，最大堆内存512MB
4. **线程池优化**: 数据同步线程池减至2个线程

## 故障排查

### 服务启动失败

```bash
# 查看详细日志
docker-compose logs backend

# 检查资源使用
docker stats

# 重启单个服务
docker-compose restart backend
```

### 数据库连接失败

```bash
# 检查 MySQL 状态
docker-compose ps mysql

# 进入 MySQL 容器检查
docker exec -it stock-mysql mysql -uroot -p -e "SHOW DATABASES;"
```

### 内存不足

如果服务器内存不足，可以：
1. 增加服务器内存到 4G
2. 或修改 `docker-compose.yml` 中的内存限制
3. 关闭不必要的服务

## 数据备份

```bash
# 备份数据库
docker exec stock-mysql mysqldump -uroot -p stock_platform > backup_$(date +%Y%m%d).sql

# 备份上传文件
tar -czvf uploads_backup_$(date +%Y%m%d).tar.gz uploads/
```

## 安全建议

1. **修改默认密码**: 务必修改 `MYSQL_ROOT_PASSWORD` 和 `JWT_SECRET`
2. **防火墙配置**: 只开放 80/443 端口，不要直接暴露 8080/9090
3. **定期更新**: 定期执行 `./deploy.sh update` 更新系统
4. **数据备份**: 设置定时任务定期备份数据库
