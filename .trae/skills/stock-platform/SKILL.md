---
name: "stock-platform"
description: "股票行情平台项目专家。包含Vue3+Spring Boot股票行情系统架构、腾讯/东方财富API对接、Docker部署配置。Invoke when working with stock-platform project for development, debugging, or deployment."
---

# Stock Platform 股票行情平台

## 项目概述

这是一个基于 Vue 3 + Spring Boot 的股票行情平台，提供实时股票数据、K线图、自选股管理等功能。

### 技术栈
- **前端**: Vue 3 + TypeScript + Element Plus + Vite
- **后端**: Spring Boot 3 + JPA + MySQL + Redis
- **部署**: Docker + Docker Compose
- **数据源**: 腾讯API (优先) + 东方财富API (备用)

## 项目结构

```
stock-platform/
├── stock-frontend/          # Vue3前端
│   ├── src/
│   │   ├── views/           # 页面组件
│   │   │   └── Home.vue     # 首页（指数、热门股票、自选股）
│   │   ├── api/             # API接口定义
│   │   │   └── stock.ts     # 股票相关API
│   │   ├── services/        # WebSocket服务
│   │   └── store/           # Pinia状态管理
│   └── Dockerfile
├── stock-backend/           # Spring Boot后端
│   ├── src/main/java/com/stock/platform/
│   │   ├── controller/      # REST API控制器
│   │   │   └── StockController.java
│   │   ├── service/         # 业务逻辑
│   │   │   ├── TencentStockDataService.java  # 腾讯API对接
│   │   │   ├── EastMoneyStockService.java    # 东方财富API对接
│   │   │   └── StockDataService.java         # 数据聚合服务
│   │   ├── dto/             # 数据传输对象
│   │   │   └── MarketIndexDTO.java
│   │   └── entity/          # 数据库实体
│   └── Dockerfile
├── docker-compose.yml       # Docker编排配置
└── docs/                    # 项目文档
```

## 关键配置

### Docker服务名
- `frontend` - 前端Nginx服务 (端口8080)
- `backend` - 后端Spring Boot服务 (端口9090)
- `mysql` - MySQL数据库
- `redis` - Redis缓存

### 数据源API

**腾讯API格式**:
- 实时数据: `https://qt.gtimg.cn/q=sh000001`
- 股票代码格式: `sh600000` (上海), `sz000001` (深圳)
- 指数代码: `sh000001`(上证), `sz399001`(深证), `sz399006`(创业板), `sh000688`(科创50)

**字段映射(大盘指数)**:
- `fields[3]` = 当前价格
- `fields[4]` = 昨收
- `fields[31]` = 涨跌额
- `fields[32]` = 涨跌幅(%)

**个股字段**:
- `fields[3]` = 当前价格
- `fields[4]` = 昨收
- `fields[31]` = 涨跌额
- `fields[32]` = 涨跌幅(%)

## 常用命令

### 本地开发
```bash
# 启动前端
cd stock-frontend && npm run dev

# 启动后端
cd stock-backend && mvn spring-boot:run
```

### Docker部署
```bash
# 完整部署
docker-compose up -d

# 只重建前端
docker-compose up -d --build --no-deps frontend

# 只重建后端
docker-compose up -d --build --no-deps backend

# 查看日志
docker-compose logs -f backend
```

### Git操作
```bash
# 推送更新
git add -A
git commit -m "feat: xxx"
git push origin main

# 服务器拉取更新
cd /www/wwwroot/stock-platform && git pull
```

## Redis操作

### 进入Redis容器
```bash
# 进入Redis容器
docker exec -it stock-redis sh

# 使用redis-cli
redis-cli

# 常用命令
KEYS *                    # 查看所有key
GET <key>                 # 获取值
DEL <key>                 # 删除key
FLUSHALL                  # 清空所有数据（慎用）
INFO                      # 查看Redis信息
MONITOR                   # 实时监控命令
```

### 从宿主机直接操作Redis
```bash
# 执行Redis命令
docker exec stock-redis redis-cli KEYS "*"

# 获取特定key的值
docker exec stock-redis redis-cli GET "stock:realtime:600519"

# 删除特定key
docker exec stock-redis redis-cli DEL "stock:cache:market-index"
```

## MySQL备份与恢复

### 备份数据库
```bash
# 方式1: 进入容器执行备份
docker exec stock-mysqldump -u root -p@Syh20050608 stock_platform > backup_$(date +%Y%m%d_%H%M%S).sql

# 方式2: 先进入容器再备份
docker exec -it stock-mysql bash
mysqldump -u root -p@Syh20050608 stock_platform > /tmp/backup.sql
exit
docker cp stock-mysql:/tmp/backup.sql ./backup.sql
```

### 恢复数据库
```bash
# 将SQL文件复制到容器
docker cp backup.sql stock-mysql:/tmp/backup.sql

# 进入容器执行恢复
docker exec -it stock-mysql bash
mysql -u root -p@Syh20050608 stock_platform < /tmp/backup.sql
```

### 查看MySQL数据
```bash
# 进入MySQL容器
docker exec -it stock-mysql mysql -u root -p@Syh20050608

# 常用命令
SHOW DATABASES;
USE stock_platform;
SHOW TABLES;
SELECT * FROM stock LIMIT 10;
SELECT * FROM stock_realtime_data WHERE symbol = '600519';
```

## 常见问题

### 1. 指数数据不正确
- 检查 `TencentStockDataService.getMarketIndex()` 字段映射
- 大盘指数: fields[31]=涨跌额, fields[32]=涨跌幅%
- 个股数据: fields[31]=涨跌额, fields[32]=涨跌幅%

### 2. 涨跌颜色显示错误
- 前端 `getPriceClass()` 直接使用后端返回的涨跌幅值
- 不要对小于1的值乘以100
- 红色=上涨(up), 绿色=下跌(down)

### 3. 服务器容器重建
```bash
# 只重建指定服务（不中断其他服务）
docker-compose stop <service-name>
docker-compose rm -f <service-name>
docker-compose build --no-cache <service-name>
docker-compose up -d <service-name>
```

### 4. 清空Redis缓存
```bash
# 清空所有缓存
docker exec stock-redis redis-cli FLUSHALL

# 清空特定前缀的key
docker exec stock-redis redis-cli --eval "return redis.call('del', unpack(redis.call('keys', 'stock:*')))"
```

## 文件位置速查

| 功能 | 文件路径 |
|------|----------|
| 前端首页 | `stock-frontend/src/views/Home.vue` |
| 腾讯API服务 | `stock-backend/src/main/java/com/stock/platform/service/TencentStockDataService.java` |
| 指数数据聚合 | `stock-backend/src/main/java/com/stock/platform/service/StockDataService.java` |
| Docker配置 | `docker-compose.yml` |
| 前端API定义 | `stock-frontend/src/api/stock.ts` |
| Skill文件 | `.trae/skills/stock-platform/SKILL.md` |

## 注意事项

1. **API优先级**: 指数数据优先使用腾讯API，个股数据优先使用腾讯API
2. **数据格式**: 腾讯API返回的涨跌幅已经是百分比值（如0.07表示0.07%）
3. **编码问题**: 腾讯API返回GBK编码，需要正确解码
4. **缓存策略**: 实时数据有60秒缓存，API失败时自动降级使用缓存
5. **数据库密码**: 生产环境请修改默认密码 `@Syh20050608`
