# Stock Platform 本地开发部署指南

## 环境要求

- **操作系统**: Windows 10/11 (WSL2)、macOS、Linux
- **Docker**: 20.10+
- **Docker Compose**: 2.0+
- **内存**: 建议 4GB+
- **磁盘空间**: 建议 10GB+

## 快速开始

### 1. 安装 Docker

#### Windows
1. 安装 [Docker Desktop for Windows](https://docs.docker.com/desktop/install/windows-install/)
2. 启用 WSL2 后端
3. 启动 Docker Desktop

#### macOS
1. 安装 [Docker Desktop for Mac](https://docs.docker.com/desktop/install/mac-install/)
2. 启动 Docker Desktop

#### Linux
```bash
# Ubuntu/Debian
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER

# 安装 Docker Compose
sudo pip3 install docker-compose
```

### 2. 克隆项目

```bash
git clone https://github.com/yhsxxlblogs/stock-platform.git
cd stock-platform
```

### 3. 一键部署

```bash
# Windows (Git Bash/WSL)
chmod +x deploy-local.sh
./deploy-local.sh deploy

# 或使用 PowerShell
# 直接执行 docker-compose 命令
docker-compose -f docker-compose.local.yml up -d
```

### 4. 访问应用

部署完成后，访问以下地址：

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端页面 | http://localhost:8080 | 主要访问入口 |
| 后端API | http://localhost:9090/api | API接口 |
| API文档 | http://localhost:9090/api/swagger-ui.html | Swagger文档 |
| MySQL | localhost:3306 | 数据库连接 |
| Redis | localhost:6379 | 缓存连接 |

**默认账号：**
- 用户名: `admin`
- 密码: `123456`

## 常用命令

```bash
# 查看服务状态
docker-compose -f docker-compose.local.yml ps

# 查看日志
docker-compose -f docker-compose.local.yml logs -f

# 查看后端日志
docker-compose -f docker-compose.local.yml logs -f backend

# 重启服务
./deploy-local.sh restart

# 停止服务
./deploy-local.sh stop

# 更新部署（修改代码后）
./deploy-local.sh update

# 完全清理（包括数据）
./deploy-local.sh clean

# 进入后端容器
docker exec -it stock-backend-local sh

# 进入 MySQL 容器
docker exec -it stock-mysql-local mysql -uroot -p
```

## 开发模式

如果你想在本地开发（不构建前端）：

### 1. 只启动数据库

```bash
docker-compose -f docker-compose.local.yml up -d mysql redis
```

### 2. 本地启动后端

```bash
cd stock-backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### 3. 本地启动前端

```bash
cd stock-frontend
npm install
npm run dev
```

访问 http://localhost:5174

## 目录说明

```
stock-platform/
├── docker-compose.local.yml    # 本地部署配置
├── deploy-local.sh             # 本地部署脚本
├── DEPLOY-LOCAL.md            # 本地部署文档
├── stock-backend/             # 后端代码
├── stock-frontend/            # 前端代码
├── uploads/                   # 上传文件目录
└── logs/                      # 日志目录
```

## 故障排查

### 端口被占用

如果提示端口被占用，修改 `docker-compose.local.yml` 中的端口映射：

```yaml
ports:
  - "8081:80"    # 改为其他端口
  - "9091:9090"  # 改为其他端口
```

### 内存不足

如果容器启动失败，可能是内存不足：

```bash
# 查看容器日志
docker-compose -f docker-compose.local.yml logs mysql

# 增加 Docker 内存限制（Docker Desktop 设置中）
```

### Windows 下文件权限问题

```powershell
# PowerShell 管理员权限执行
wsl --shutdown
# 重启 Docker Desktop
```

## 与服务器部署的区别

| 特性 | 本地部署 | 服务器部署 |
|------|----------|------------|
| 配置文件 | docker-compose.local.yml | docker-compose.yml |
| 端口暴露 | MySQL/Redis 暴露到宿主机 | 仅暴露前端端口 |
| 内存限制 | 无限制 | 有资源限制 |
| 数据卷 | 带 _local 后缀 | 默认名称 |
| 重启策略 | unless-stopped | unless-stopped |

## 更新代码后重新部署

```bash
# 拉取最新代码
git pull origin main

# 重新部署
./deploy-local.sh update
```

## 数据持久化

数据存储在 Docker Volume 中：

- `mysql_data_local`: MySQL 数据
- `redis_data_local`: Redis 数据
- `uploads_data_local`: 上传的文件

即使删除容器，数据也会保留。要完全清理数据：

```bash
./deploy-local.sh clean
```
