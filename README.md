# MarketPulse 股票交易平台

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-green.svg)](https://spring.io/projects/spring-boot)
[![Vue.js](https://img.shields.io/badge/Vue.js-3.4-blue.svg)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.3-blue.svg)](https://www.typescriptlang.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-orange.svg)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-20.10+-blue.svg)](https://www.docker.com/)

一个功能完善的综合性股票交易平台，提供实时行情数据、K线图表、自选股管理等功能。

## 功能特性

- **实时行情** - WebSocket推送实时股票数据
- **K线图表** - 集成ECharts展示日K、周K、月K数据
- **股票搜索** - 智能搜索支持股票代码和名称
- **自选股** - 用户可添加关注股票
- **用户系统** - JWT认证，支持注册、登录

## 技术栈

### 后端
- Spring Boot 3.2
- Spring Security + JWT
- Spring Data JPA
- MySQL 8.0
- Redis 7
- WebSocket

### 前端
- Vue.js 3.4
- TypeScript 5.3
- Element Plus
- ECharts
- Vite

## 快速部署

### 方式一：Docker Compose（推荐）

```bash
# 1. 克隆项目
git clone https://github.com/yhsxxlblogs/stock-platform.git
cd stock-platform

# 2. 构建并启动
docker-compose up -d

# 3. 查看状态
docker-compose ps
```

### 方式二：手动构建

```bash
# 构建后端
cd stock-backend
docker build -t stock-platform-backend:latest .

# 构建前端
cd ../stock-frontend
docker build -t stock-platform-frontend:latest .

# 启动
cd ..
docker-compose up -d
```

## 访问地址

- 前端页面：http://localhost:8080
- 后端API：http://localhost:9090/api
- API文档：http://localhost:9090/api/swagger-ui.html

## 生产部署

### 1. 配置 Nginx

将 `nginx/bt-nginx.conf` 复制到宝塔面板或 Nginx 配置目录。

### 2. 配置域名

修改 `nginx/bt-nginx.conf` 中的 `server_name` 为你的域名。

### 3. 启动服务

```bash
docker-compose up -d
```

## 项目结构

```
stock-platform/
├── stock-backend/          # Spring Boot 后端
├── stock-frontend/         # Vue3 前端
├── nginx/                  # Nginx 配置
├── docker-compose.yml      # Docker 编排配置
├── DEPLOY.md              # 部署文档
└── README.md              # 项目说明
```

## 环境变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| MYSQL_ROOT_PASSWORD | @Syh20050608 | MySQL root 密码 |
| MYSQL_DATABASE | stock_platform | 数据库名 |
| JWT_SECRET | stockPlatformSecretKey | JWT 密钥 |
| JWT_EXPIRATION | 86400000 | Token 过期时间 |

## 常用命令

```bash
# 查看日志
docker-compose logs -f

# 重启服务
docker-compose restart

# 停止服务
docker-compose down

# 进入容器
docker exec -it stock-backend bash
```

## 许可证

[MIT](LICENSE)
