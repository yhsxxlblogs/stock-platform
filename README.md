# MarketPulse 股票交易平台

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-green.svg)](https://spring.io/projects/spring-boot)
[![Vue.js](https://img.shields.io/badge/Vue.js-3.4-blue.svg)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.3-blue.svg)](https://www.typescriptlang.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-orange.svg)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-20.10+-blue.svg)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

一个功能完善的综合性股票交易平台，提供实时行情数据、K线图表、自选股管理等功能。采用前后端分离架构，支持Docker一键部署。

## 功能特性

### 核心功能
- **实时行情** - WebSocket推送实时股票数据，支持大盘指数和个股行情
- **K线图表** - 集成ECharts展示日K、周K、月K数据，支持缩放和指标分析
- **股票搜索** - 智能搜索支持股票代码和名称，自动补全提示
- **自选股** - 用户可添加关注股票，实时跟踪自选组合
- **用户系统** - JWT认证，支持注册、登录、个人信息管理

### 数据覆盖
- A股全市场5000+只股票
- 实时行情数据（价格、涨跌幅、成交量等）
- 历史K线数据
- 买卖五档盘口数据
- 四大指数（上证指数、深证成指、创业板指、科创50）

## 技术架构

### 后端技术栈
| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.0 | 核心框架 |
| Spring Security | 6.2 | 安全认证 |
| Spring Data JPA | 3.2 | 数据访问 |
| MySQL | 8.0 | 关系型数据库 |
| Redis | 7.0 | 缓存与会话 |
| WebSocket | - | 实时数据推送 |
| JWT | 0.12 | Token认证 |
| Maven | 3.9 | 构建工具 |

### 前端技术栈
| 技术 | 版本 | 用途 |
|------|------|------|
| Vue.js | 3.4 | 前端框架 |
| TypeScript | 5.3 | 类型系统 |
| Element Plus | 2.5 | UI组件库 |
| ECharts | 5.4 | 图表库 |
| Vite | 5.0 | 构建工具 |
| Pinia | 2.1 | 状态管理 |
| Axios | 1.6 | HTTP客户端 |

### 系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│                         客户端 (Browser)                      │
│                    Vue 3 + Element Plus                      │
└─────────────────────────┬───────────────────────────────────┘
                          │ HTTPS/WebSocket
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                      Nginx (Reverse Proxy)                   │
│              静态资源服务 / API代理 / 负载均衡                │
└─────────────────────────┬───────────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        │                 │                 │
        ▼                 ▼                 ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│   Frontend   │  │    Backend   │  │    MySQL     │
│   (Docker)   │  │   (Docker)   │  │   (Docker)   │
│  Vue.js App  │  │ Spring Boot  │  │    8.0+      │
│   Port 80    │  │  Port 9090   │  │  Port 3306   │
└──────────────┘  └──────┬───────┘  └──────────────┘
                         │
                    ┌────┴────┐
                    ▼         ▼
              ┌─────────┐  ┌─────────┐
              │  Redis  │  │WebSocket│
              │ (Cache) │  │ (Push)  │
              └─────────┘  └─────────┘
```

## 快速开始

### 方式一：Docker部署（推荐）

**环境要求**
- Docker Engine 20.10+
- Docker Compose 2.0+
- 4GB+ 内存
- 10GB+ 磁盘空间

**部署步骤**

```bash
# 1. 进入项目目录
cd stock-platform

# 2. 启动所有服务
docker-compose up -d

# 3. 查看服务状态
docker-compose ps

# 4. 查看日志
docker-compose logs -f
```

**访问地址**
- 前端页面：http://localhost
- 后端API：http://localhost:9090/api
- API文档：http://localhost:9090/api/swagger-ui.html

### 方式二：开发环境部署

**后端启动**
```bash
cd stock-backend

# 使用Maven Wrapper（无需安装Maven）
./mvnw spring-boot:run

# 或打包后运行
./mvnw clean package -DskipTests
java -jar target/stock-backend-1.0.0.jar
```

**前端启动**
```bash
cd stock-frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build
```

## 项目结构

```
stock-platform/
├── stock-backend/                 # Spring Boot后端
│   ├── src/main/java/com/stock/platform/
│   │   ├── config/               # 配置类（WebSocket、Security等）
│   │   ├── controller/           # REST API控制器
│   │   ├── service/              # 业务逻辑层
│   │   ├── repository/           # 数据访问层（JPA）
│   │   ├── entity/               # 数据库实体
│   │   ├── dto/                  # 数据传输对象
│   │   ├── security/             # JWT认证相关
│   │   └── exception/            # 全局异常处理
│   ├── src/main/resources/
│   │   ├── application.yml       # 主配置文件
│   │   └── application-docker.yml # Docker环境配置
│   ├── Dockerfile                # 后端镜像构建
│   ├── pom.xml                   # Maven配置
│   └── mvnw/mvnw.cmd             # Maven Wrapper
│
├── stock-frontend/               # Vue3前端
│   ├── src/
│   │   ├── api/                  # API接口封装
│   │   ├── views/                # 页面组件
│   │   ├── layouts/              # 布局组件
│   │   ├── router/               # 路由配置
│   │   ├── store/                # Pinia状态管理
│   │   ├── services/             # WebSocket服务
│   │   └── utils/                # 工具函数
│   ├── Dockerfile                # 前端镜像构建
│   ├── nginx.conf                # Nginx配置
│   └── package.json              # NPM配置
│
├── docker-compose.yml            # Docker编排配置
├── docker-compose.dev.yml        # 开发环境配置
├── init.sql                      # 数据库初始化脚本
├── mysql.cnf                     # MySQL配置
└── README.md                     # 项目说明
```

## 配置说明

### 环境变量

编辑 `.env` 文件配置以下参数：

```env
# MySQL配置
MYSQL_ROOT_PASSWORD=your_password
MYSQL_DATABASE=stock_platform

# JWT配置
JWT_SECRET=your_jwt_secret_key
JWT_EXPIRATION=86400000

# 文件上传路径
UPLOAD_DIR=/app/uploads
```

### 后端配置

**application.yml**
```yaml
server:
  port: 9090

spring:
  datasource:
    url: jdbc:mysql://mysql:3306/stock_platform?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: ${MYSQL_ROOT_PASSWORD}
  
  redis:
    host: redis
    port: 6379

# JWT配置
jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION}
```

## API文档

启动后端服务后，访问 Swagger UI 查看完整API文档：

```
http://localhost:9090/api/swagger-ui.html
```

### 主要接口

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/auth/register | POST | 用户注册 |
| /api/auth/login | POST | 用户登录 |
| /api/auth/me | GET | 获取当前用户 |
| /api/stocks/public/list | GET | 获取股票列表 |
| /api/stocks/public/search | GET | 搜索股票 |
| /api/stocks/public/suggest | GET | 搜索建议 |
| /api/stocks/public/{symbol} | GET | 股票详情 |
| /api/stocks/public/{symbol}/kline | GET | K线数据 |
| /api/favorites | GET | 获取自选股 |
| /api/favorites/{stockId} | POST | 添加自选股 |
| /api/ws/stock | WebSocket | 实时数据推送 |

## 数据库设计

### 核心表结构

**users** - 用户表
- id, username, email, password, avatar, status, created_at

**stocks** - 股票主表
- id, symbol, name, exchange, industry, market_cap, status

**stock_basic** - A股基础信息表
- symbol, name, exchange, market_type, industry, status

**stock_realtime_data** - 实时数据表
- stock_id, current_price, change_price, volume, bid/ask五档数据

**user_favorites** - 用户自选股表
- id, user_id, stock_id, created_at

完整数据库文档见：[DATABASE_SCHEMA.md](DATABASE_SCHEMA.md)

## 开发指南

### 开发环境启动

```bash
# 使用开发环境配置（支持热更新）
docker-compose -f docker-compose.dev.yml up -d

# 或使用脚本
./dev-start.sh  # Linux/Mac
dev-start.bat   # Windows
```

### 代码规范

- 后端：遵循阿里巴巴Java开发手册
- 前端：ESLint + Prettier 代码格式化
- 提交：使用语义化提交信息

### 测试

```bash
# 后端测试
cd stock-backend
./mvnw test

# 前端测试
cd stock-frontend
npm run test
```

## 生产部署

### 构建镜像

```bash
# 构建所有镜像
docker-compose build

# 推送镜像到仓库
docker push your-registry/stock-backend:1.0.0
docker push your-registry/stock-frontend:1.0.0
```

### 安全建议

1. **修改默认密码** - 生产环境务必修改所有默认密码
2. **配置HTTPS** - 使用SSL证书加密通信
3. **JWT密钥** - 使用随机生成的强密钥
4. **访问控制** - 配置防火墙规则，限制端口访问
5. **日志监控** - 配置日志收集和告警

### 性能优化

- **数据库** - 添加索引优化查询性能
- **缓存** - Redis缓存热点数据
- **CDN** - 静态资源使用CDN加速
- **连接池** - 配置HikariCP连接池参数

## 常见问题

**Q: 数据库连接失败？**
A: 检查MySQL容器是否运行正常，确认数据库密码配置正确。

**Q: 前端无法访问后端API？**
A: 检查Nginx配置中的代理地址，确认后端服务端口映射正确。

**Q: WebSocket连接失败？**
A: 检查防火墙是否放行WebSocket端口，确认Nginx代理配置支持WebSocket。

**Q: 如何导入股票数据？**
A: 系统启动后会自动从东方财富API同步A股列表，也可手动调用同步接口。

## 更新日志

### v1.0.0 (2026-04-18)
- 初始版本发布
- 实现用户注册/登录
- 实现股票行情展示
- 实现K线图表
- 实现自选股功能
- 实现WebSocket实时推送
- 支持Docker部署

## 贡献指南

欢迎提交Issue和Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 许可证

本项目采用 [MIT](LICENSE) 许可证开源。

## 致谢

- 股票数据来源于东方财富、腾讯财经API
- 图表库使用 Apache ECharts
- UI组件库使用 Element Plus

---

**项目主页**: https://github.com/yourusername/stock-platform  
**问题反馈**: https://github.com/yourusername/stock-platform/issues  
**文档中心**: https://docs.yourdomain.com
