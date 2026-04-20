# Stock Platform 开发文档

## 项目概述

Stock Platform 是一个专业的股票行情分析平台，提供实时行情数据、智能分析工具、自选股管理等功能。项目采用前后端分离架构，支持Docker容器化部署。

## 技术栈

### 后端技术栈
- **框架**: Spring Boot 3.x
- **语言**: Java 17
- **数据库**: MySQL 8.0
- **缓存**: Redis 7.x
- **ORM**: Spring Data JPA
- **安全**: Spring Security + JWT
- **实时通信**: WebSocket
- **构建工具**: Maven

### 前端技术栈
- **框架**: Vue 3 + TypeScript
- **UI组件库**: Element Plus
- **构建工具**: Vite
- **状态管理**: Pinia
- **图表库**: ECharts
- **HTTP客户端**: Axios
- **WebSocket**: 原生WebSocket API

### 部署技术
- **容器化**: Docker + Docker Compose
- **Web服务器**: Nginx
- **反向代理**: Nginx

## 项目结构

```
stock-platform/
├── stock-backend/              # 后端项目
│   ├── src/main/java/
│   │   └── com/stock/platform/
│   │       ├── annotation/     # 自定义注解
│   │       ├── config/         # 配置类
│   │       ├── controller/     # 控制器
│   │       ├── dto/            # 数据传输对象
│   │       ├── entity/         # 实体类
│   │       ├── repository/     # 数据访问层
│   │       ├── security/       # 安全配置
│   │       ├── service/        # 业务逻辑层
│   │       └── websocket/      # WebSocket处理器
│   ├── src/main/resources/
│   │   ├── stocks-data.json    # 股票数据文件
│   │   ├── application.yml     # 主配置文件
│   │   └── application-docker.yml # Docker环境配置
│   └── pom.xml
├── stock-frontend/             # 前端项目
│   ├── src/
│   │   ├── api/                # API接口
│   │   ├── assets/             # 静态资源
│   │   ├── components/         # 组件
│   │   ├── layouts/            # 布局组件
│   │   ├── router/             # 路由配置
│   │   ├── services/           # 服务层
│   │   ├── store/              # 状态管理
│   │   ├── utils/              # 工具函数
│   │   └── views/              # 页面视图
│   ├── package.json
│   └── vite.config.ts
├── scripts/                    # 脚本文件
│   └── fetch_all_stocks.py     # 股票数据爬虫
├── docker-compose.yml          # Docker Compose配置
├── docker-compose.local.yml    # 本地开发配置
├── deploy.sh                   # 部署脚本
└── README.md
```

## 核心功能模块

### 1. 用户认证模块
- **注册**: 用户名、密码、邮箱验证
- **登录**: JWT Token认证
- **权限控制**: 基于Spring Security的权限管理
- **密码加密**: BCrypt加密存储

### 2. 股票数据模块
- **数据同步**: 容器启动时自动从JSON文件加载5723只股票
- **实时行情**: 通过腾讯API获取实时数据
- **K线数据**: 支持日K、周K、月K
- **分时数据**: 实时分时走势图
- **技术指标**: MACD、KDJ、RSI等

### 3. 自选股模块
- **添加/删除**: 用户可管理自选股列表
- **实时推送**: WebSocket推送自选股价格变动
- **数据持久化**: 存储到MySQL数据库

### 4. 搜索模块
- **自动补全**: 输入时实时提示股票
- **模糊搜索**: 支持代码和名称搜索
- **高亮显示**: 搜索结果关键词高亮

### 5. WebSocket实时通信
- **连接管理**: 用户连接时自动订阅自选股
- **消息推送**: 价格变动实时推送
- **心跳机制**: 保持连接活跃

## 数据库设计

### 核心表结构

#### 1. users (用户表)
```sql
- id: BIGINT PK
- username: VARCHAR(50) UNIQUE
- password: VARCHAR(255)
- email: VARCHAR(100)
- avatar: VARCHAR(255)
- created_at: DATETIME
- updated_at: DATETIME
```

#### 2. stock_basic (股票基础信息表)
```sql
- id: BIGINT PK
- symbol: VARCHAR(20) UNIQUE
- name: VARCHAR(100)
- exchange: VARCHAR(10)
- market_type: VARCHAR(20)
- industry: VARCHAR(50)
- status: INT
- created_at: DATETIME
- updated_at: DATETIME
```

#### 3. stocks (股票表)
```sql
- id: BIGINT PK
- symbol: VARCHAR(20)
- name: VARCHAR(100)
- exchange: VARCHAR(10)
- industry: VARCHAR(50)
- current_price: DECIMAL(10,2)
- change_percent: DECIMAL(5,2)
- volume: BIGINT
- status: INT
- created_at: DATETIME
- updated_at: DATETIME
```

#### 4. favorites (自选股表)
```sql
- id: BIGINT PK
- user_id: BIGINT FK
- stock_id: BIGINT FK
- symbol: VARCHAR(20)
- created_at: DATETIME
```

## API接口设计

### 认证接口
- `POST /api/auth/register` - 用户注册
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/logout` - 用户登出
- `GET /api/auth/profile` - 获取用户信息
- `PUT /api/auth/profile` - 更新用户信息
- `POST /api/auth/avatar` - 上传头像

### 股票接口
- `GET /api/stocks/public/list` - 获取股票列表（公开）
- `GET /api/stocks/public/{symbol}` - 获取单只股票（公开）
- `GET /api/stocks/public/search` - 搜索股票（公开）
- `GET /api/stocks/public/suggest` - 自动补全（公开）
- `GET /api/stocks/public/{symbol}/detail` - 股票详情（公开）
- `GET /api/stocks/public/market-index` - 大盘指数（公开）
- `GET /api/stocks/public/stock-stats` - 股票统计（公开）

### 自选股接口
- `GET /api/favorites` - 获取自选股列表
- `POST /api/favorites` - 添加自选股
- `DELETE /api/favorites/{id}` - 删除自选股
- `GET /api/favorites/check/{symbol}` - 检查是否已自选

### WebSocket接口
- `WS /api/ws/stock` - 股票实时数据推送

## 关键代码实现

### 1. 股票数据初始化

```java
@Service
@Slf4j
public class StockDataInitializer {
    
    @PostConstruct
    public void init() {
        // 延迟5秒执行，等待数据库连接就绪
        new Thread(() -> {
            TimeUnit.SECONDS.sleep(5);
            checkAndInitializeStockData();
        }).start();
    }
    
    private void checkAndInitializeStockData() {
        long currentCount = stockBasicRepository.count();
        if (currentCount < 500) {
            // 从JSON文件加载5723只股票
            loadStockDataFromLocalFile();
        }
    }
}
```

### 2. WebSocket实时推送

```java
@Component
@Slf4j
public class StockWebSocketHandler extends TextWebSocketHandler {
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 用户连接时订阅自选股
        List<String> favorites = getUserFavorites(session);
        subscribeStocks(session, favorites);
    }
    
    @Scheduled(fixedRate = 3000)
    public void pushPriceUpdates() {
        // 每3秒推送一次价格更新
        for (WebSocketSession session : sessions) {
            List<String> symbols = getSubscribedStocks(session);
            Map<String, Object> data = fetchRealtimeData(symbols);
            session.sendMessage(new TextMessage(JSON.toJSONString(data)));
        }
    }
}
```

### 3. 前端WebSocket服务

```typescript
class WebSocketService {
    private ws: WebSocket | null = null;
    private reconnectAttempts = 0;
    private maxReconnectAttempts = 5;
    
    connect() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/api/ws/stock`;
        
        this.ws = new WebSocket(wsUrl);
        
        this.ws.onopen = () => {
            console.log('WebSocket连接成功');
            this.reconnectAttempts = 0;
        };
        
        this.ws.onmessage = (event) => {
            const data = JSON.parse(event.data);
            this.handleMessage(data);
        };
        
        this.ws.onclose = () => {
            this.reconnect();
        };
    }
    
    private reconnect() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            setTimeout(() => {
                this.reconnectAttempts++;
                this.connect();
            }, 3000);
        }
    }
}
```

## 开发规范

### 代码规范
1. **命名规范**:
   - 类名：大驼峰（StockController）
   - 方法名：小驼峰（getStockBySymbol）
   - 常量：全大写下划线（MAX_RETRY_COUNT）
   - 数据库字段：下划线命名（created_at）

2. **注释规范**:
   - 类注释：说明类的作用
   - 方法注释：说明方法功能、参数、返回值
   - 复杂逻辑：添加行内注释

3. **异常处理**:
   - 使用全局异常处理器
   - 记录异常日志
   - 返回友好的错误信息

### Git提交规范
```
feat: 新功能
fix: 修复bug
docs: 文档更新
style: 代码格式（不影响功能）
refactor: 重构
test: 测试相关
chore: 构建过程或辅助工具的变动
```

## 数据更新策略

### 实时数据更新流程

```
┌─────────────────────────────────────────────────────────────┐
│                     数据更新架构图                            │
└─────────────────────────────────────────────────────────────┘

交易时间 (9:30-11:30, 13:00-15:00)
├── 定时任务 (每5秒)
│   └── StockDataScheduler.updateRealtimeData()
│       └── 更新所有股票到数据库
├── 用户请求
│   ├── 自选股列表 → 直接调用API获取最新数据
│   ├── 热门股票 → 从数据库读取 + API缓存
│   └── 个股详情 → 直接调用API获取
└── WebSocket推送 (每2秒)
    └── 从数据库读取推送

收盘时间 (其他时间)
├── 定时任务: 停止更新
├── 用户请求
│   ├── 自选股列表 → 调用API获取并更新数据库
│   ├── 热门股票 → 从Redis缓存/数据库读取
│   └── 个股详情 → 调用API获取
└── WebSocket推送: 继续从数据库推送
```

### 数据获取优先级

#### 实时数据（盘中价格波动）
**不使用Redis缓存**，通过WebSocket实时推送

1. **自选股列表** (`UserFavoriteController.getFavorites()`)
   - 第一优先级: 腾讯API实时数据
   - 第二优先级: 数据库缓存
   - 获取后更新数据库（收盘后保留最新数据）

2. **热门股票** (`StockController.getHotStocks()`)
   - 第一优先级: 数据库缓存
   - 交易时间由定时任务每5秒更新

3. **个股详情** (`StockController.getStockDetail()`)
   - 第一优先级: 腾讯API实时数据
   - 第二优先级: 数据库缓存

4. **大盘指数** (`StockController.getMarketIndex()`)
   - 第一优先级: 腾讯API实时数据
   - 第二优先级: 数据库缓存

#### 历史数据（K线图）
**使用Redis缓存**，历史数据高度一致

1. **K线数据** (`StockController.getKlineData()`)
   - 第一优先级: Redis缓存 (24小时)
   - 第二优先级: 数据库/第三方API
   - 每日收盘后清除缓存，次日重新获取

### Redis缓存策略

| 缓存类型 | Key | 过期时间 | 说明 |
|---------|-----|---------|------|
| K线数据 | `stock:kline:{symbol}:{period}` | 24小时 | K线历史数据，收盘后清除 |
| 股票列表 | `stock:list` | 30分钟 | 所有股票基础信息 |
| 股票详情 | `stock:detail:{symbol}` | 60分钟 | 单只股票详细信息 |
| 搜索结果 | `search:{keyword}` | 30分钟 | 搜索关键词结果 |
| 用户信息 | `user:info:{userId}` | 120分钟 | 用户基本信息 |
| 自选股 | `user:favorites:{userId}` | 30分钟 | 用户自选股列表 |

**注意**: 实时数据（价格、涨跌幅、成交量等）不使用Redis缓存，通过WebSocket实时推送

## 性能优化

### 后端优化
1. **数据库连接池**: HikariCP，最大连接数10
2. **Redis缓存**: 多级缓存策略（API → Redis → 数据库）
3. **JVM参数**: -Xms256m -Xmx512m，使用G1GC
4. **线程池**: 数据同步使用2线程（适配2核服务器）
5. **批量处理**: API请求批量获取，减少网络开销
6. **缓存预热**: 定时任务在交易时间预热热点数据

### 前端优化
1. **懒加载**: 路由组件懒加载
2. **CDN**: 静态资源使用CDN
3. **压缩**: Gzip压缩传输
4. **缓存**: 浏览器缓存策略

### 部署优化
1. **Docker多阶段构建**: 减小镜像体积
2. **资源限制**: 容器CPU和内存限制
3. **健康检查**: 自动重启不健康容器

## 安全设计

1. **JWT认证**: Token有效期24小时
2. **密码加密**: BCrypt加密存储
3. **CORS配置**: 允许跨域访问
4. **SQL注入防护**: 使用JPA参数化查询
5. **XSS防护**: 前端转义输出

## 监控与日志

1. **日志级别**:
   - ERROR: 错误日志
   - WARN: 警告日志
   - INFO: 信息日志
   - DEBUG: 调试日志

2. **关键日志**:
   - 用户登录/登出
   - 股票数据同步
   - WebSocket连接/断开
   - 异常信息

## 扩展性设计

1. **模块化**: 功能模块独立，便于扩展
2. **配置化**: 关键参数配置化
3. **插件化**: 支持新增数据源
4. **水平扩展**: 支持多实例部署

## 开发环境搭建

### 本地开发
```bash
# 1. 启动数据库和缓存
docker-compose -f docker-compose.local.yml up -d mysql redis

# 2. 启动后端
cd stock-backend
./mvnw spring-boot:run

# 3. 启动前端
cd stock-frontend
npm install
npm run dev
```

### 调试技巧
1. **后端调试**: 使用IDE远程调试
2. **前端调试**: Vue DevTools + Chrome DevTools
3. **API测试**: Postman / Swagger UI
4. **数据库**: MySQL Workbench

## 常见问题

### 1. 数据库连接失败
- 检查MySQL服务是否启动
- 检查数据库配置
- 检查网络连接

### 2. WebSocket连接失败
- 检查Nginx代理配置
- 检查防火墙设置
- 检查Token是否过期

### 3. 股票数据不同步
- 检查JSON文件是否存在
- 检查数据库连接
- 查看同步日志

## 版本历史

### v1.0.0 (2024-01)
- 基础功能完成
- 用户认证
- 股票查看
- 自选股管理

### v1.1.0 (2024-02)
- 添加WebSocket实时推送
- 优化前端UI
- 添加K线图

### v1.2.0 (2024-03)
- 添加5723只股票数据
- 容器启动自动同步
- 优化部署流程

## 联系方式

- 项目地址: https://github.com/yhsxxlblogs/stock-platform
- 问题反馈: 提交GitHub Issue
