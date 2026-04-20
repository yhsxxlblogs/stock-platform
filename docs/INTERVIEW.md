# Stock Platform 面试问题文档

## 一、项目概述类问题

### Q1: 请介绍一下这个项目
**回答要点:**
- Stock Platform是一个专业的股票行情分析平台
- 采用前后端分离架构，后端Spring Boot + 前端Vue3
- 核心功能：实时行情、自选股管理、K线图、技术指标
- 特色：容器启动自动同步5723只股票数据、WebSocket实时推送
- 部署：Docker容器化，支持2核2G服务器

### Q2: 为什么选择这个技术栈？
**回答要点:**
- **Spring Boot**: 快速开发、生态丰富、微服务友好
- **Vue3**: 组合式API更灵活、TypeScript支持好、性能优秀
- **MySQL**: 成熟稳定、社区活跃、适合关系型数据
- **Redis**: 高性能缓存、支持多种数据结构
- **Docker**: 环境一致性、便于部署、资源隔离

### Q3: 项目的主要难点是什么？
**回答要点:**
1. **WebSocket实时推送**: 需要维护大量连接，设计心跳机制和重连策略
2. **股票数据同步**: 5723只股票的数据初始化和增量更新
3. **性能优化**: 2核2G服务器上的资源限制和优化
4. **跨域问题**: 开发环境和生产环境的CORS配置

---

## 二、后端技术问题

### Q4: 为什么选择JWT而不是Session？
**回答要点:**
- **无状态**: 服务器不需要存储会话信息，适合分布式部署
- **跨域友好**: 天然支持跨域，适合前后端分离
- **性能**: 不需要查询数据库验证会话
- **实现**: 使用jjwt库，Token包含用户信息和过期时间

**代码示例:**
```java
// Token生成
String token = Jwts.builder()
    .setSubject(user.getUsername())
    .claim("userId", user.getId())
    .setIssuedAt(new Date())
    .setExpiration(new Date(System.currentTimeMillis() + 86400000))
    .signWith(key, SignatureAlgorithm.HS256)
    .compact();
```

### Q5: 如何实现股票数据的实时推送？
**回答要点:**
1. **WebSocket连接**: 用户登录后建立WebSocket连接
2. **订阅机制**: 连接时自动订阅用户的自选股
3. **定时推送**: 使用@Scheduled每3秒推送一次价格更新
4. **数据格式**: JSON格式，包含股票代码、价格、涨跌幅等

**关键代码:**
```java
@Scheduled(fixedRate = 3000)
public void pushPriceUpdates() {
    for (WebSocketSession session : sessions) {
        List<String> symbols = getSubscribedStocks(session);
        Map<String, Object> data = fetchRealtimeData(symbols);
        session.sendMessage(new TextMessage(JSON.toJSONString(data)));
    }
}
```

### Q6: 容器启动时如何自动同步股票数据？
**回答要点:**
1. **@PostConstruct**: 应用启动后自动执行初始化
2. **数据检查**: 检查数据库中股票数量是否小于500
3. **JSON文件**: 从resources/stocks-data.json读取5723只股票
4. **批量插入**: 分批保存到数据库，避免内存溢出

**关键代码:**
```java
@PostConstruct
public void init() {
    new Thread(() -> {
        TimeUnit.SECONDS.sleep(5); // 等待数据库就绪
        checkAndInitializeStockData();
    }).start();
}
```

### Q7: 如何处理高并发请求？
**回答要点:**
1. **数据库连接池**: HikariCP，限制最大连接数10
2. **Redis缓存**: 缓存热点数据，减少数据库查询
3. **异步处理**: 使用@Async处理耗时操作
4. **资源限制**: Docker容器CPU和内存限制
5. **线程池**: 数据同步使用固定大小线程池

### Q8: 项目的安全设计有哪些？
**回答要点:**
1. **JWT认证**: Token-based认证，支持过期和刷新
2. **密码加密**: BCrypt加密，防止明文存储
3. **CORS配置**: 配置允许的域名，防止跨域攻击
4. **SQL注入防护**: 使用JPA参数化查询
5. **接口权限**: Spring Security控制接口访问权限

### Q9: 数据库表设计有哪些考虑？
**回答要点:**
1. **索引设计**: symbol字段建立唯一索引，提高查询效率
2. **字段类型**: 价格使用DECIMAL避免浮点数精度问题
3. **时间戳**: created_at和updated_at记录数据变更
4. **软删除**: status字段控制数据状态，不物理删除
5. **外键约束**: favorites表关联users和stocks表

### Q10: 如何实现股票搜索的自动补全？
**回答要点:**
1. **数据库查询**: 使用LIKE模糊查询symbol和name字段
2. **索引优化**: 为symbol和name字段建立索引
3. **前端防抖**: 输入延迟300ms后发送请求
4. **结果限制**: 最多返回10条建议
5. **高亮显示**: 前端对匹配关键词高亮

---

## 三、前端技术问题

### Q11: Vue3相比Vue2有哪些优势？
**回答要点:**
1. **组合式API**: 更灵活的代码组织方式，便于逻辑复用
2. **TypeScript支持**: 更好的类型推断和代码提示
3. **性能优化**: 更小的包体积、更快的渲染速度
4. **Teleport**: 更方便的DOM操作
5. **Fragments**: 支持多根节点组件

### Q12: 如何管理前端状态？
**回答要点:**
- 使用Pinia替代Vuex
- **User Store**: 管理用户登录状态和信息
- **持久化**: localStorage存储Token，pinia-plugin-persistedstate持久化用户状态
- **响应式**: 使用computed计算登录状态

**代码示例:**
```typescript
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token'),
    userInfo: null
  }),
  getters: {
    isLoggedIn: (state) => !!state.token
  },
  actions: {
    setToken(token: string) {
      this.token = token
      localStorage.setItem('token', token)
    }
  },
  persist: true
})
```

### Q13: 如何实现WebSocket的前端连接管理？
**回答要点:**
1. **单例模式**: WebSocketService作为单例服务
2. **自动重连**: 连接断开时自动重连，最多5次
3. **心跳机制**: 定期发送心跳保持连接
4. **消息处理**: 根据消息类型分发到不同处理器
5. **组件订阅**: 组件挂载时订阅，卸载时取消订阅

### Q14: 前端如何进行性能优化？
**回答要点:**
1. **路由懒加载**: 使用import()动态导入组件
2. **CDN加速**: 静态资源使用CDN
3. **Gzip压缩**: 启用Nginx Gzip压缩
4. **缓存策略**: 合理设置浏览器缓存
5. **代码分割**: Vite自动代码分割

### Q15: 如何实现K线图的绘制？
**回答要点:**
1. **ECharts**: 使用ECharts库绘制K线图
2. **数据格式**: 开盘价、收盘价、最低价、最高价、成交量
3. **图表切换**: 支持K线图和分时图切换
4. **技术指标**: 叠加MACD、KDJ等指标
5. **实时更新**: WebSocket推送新数据时更新图表

---

## 四、部署运维问题

### Q16: 如何部署到2核2G的服务器？
**回答要点:**
1. **资源限制**: Docker容器限制CPU和内存使用
2. **JVM优化**: -Xms256m -Xmx512m，使用G1GC
3. **连接池优化**: MySQL连接池最大10个连接
4. **线程池优化**: 数据同步使用2线程
5. **健康检查**: 配置合理的健康检查间隔

**docker-compose配置:**
```yaml
deploy:
  resources:
    limits:
      cpus: '0.75'
      memory: 640M
```

### Q17: Docker多阶段构建的优势是什么？
**回答要点:**
1. **减小镜像体积**: 只保留运行时需要的文件
2. **安全性**: 不包含编译工具和源代码
3. **分层缓存**: 构建步骤分层，提高构建速度
4. **一致性**: 确保构建环境和运行环境一致

### Q18: 如何实现零停机部署？
**回答要点:**
1. **蓝绿部署**: 准备两套环境，切换流量
2. **滚动更新**: Docker Compose逐个更新容器
3. **健康检查**: 新容器健康后再停止旧容器
4. **负载均衡**: Nginx反向代理，动态切换后端

### Q19: 项目监控和日志如何设计？
**回答要点:**
1. **日志级别**: ERROR、WARN、INFO、DEBUG分级
2. **日志收集**: 使用docker logs收集容器日志
3. **关键指标**: 监控CPU、内存、数据库连接数
4. **告警机制**: 异常情况发送告警通知

### Q20: 如何处理数据库备份和恢复？
**回答要点:**
1. **定时备份**: 使用cron定时执行mysqldump
2. **数据卷**: Docker volume持久化数据
3. **异地备份**: 备份文件同步到远程存储
4. **恢复测试**: 定期测试备份文件可恢复性

---

## 五、项目亮点和难点

### 项目亮点
1. **完整的数据链路**: 从数据爬取、存储、展示到实时推送
2. **容器化部署**: 一键部署，环境一致性
3. **性能优化**: 适配低配置服务器
4. **用户体验**: 实时推送、自动补全、K线图
5. **代码质量**: 规范的项目结构、完善的注释

### 技术难点
1. **WebSocket高并发**: 维护大量长连接，设计合理的推送策略
2. **大数据量处理**: 5723只股票的初始化和更新
3. **资源限制**: 2核2G服务器上的性能优化
4. **数据一致性**: 多数据源的数据同步和一致性保证

---

## 六、开放性问题

### Q21: 如果要扩展这个项目，你会怎么做？
**回答要点:**
1. **功能扩展**: 添加股票筛选、策略回测、智能推荐
2. **技术升级**: 引入Kafka处理高并发、Elasticsearch优化搜索
3. **移动端**: 开发小程序或App
4. **多数据源**: 支持多个股票数据源，提高数据可靠性
5. **AI集成**: 使用机器学习预测股价走势

### Q22: 项目中遇到的最大挑战是什么？
**回答要点:**
- **挑战**: WebSocket实时推送的稳定性
- **原因**: 网络波动、服务器重启、客户端断网等情况
- **解决**: 
  1. 设计心跳机制检测连接状态
  2. 实现自动重连机制
  3. 使用Redis存储订阅关系，支持分布式部署
  4. 添加日志监控，及时发现问题

### Q23: 如何保证数据的实时性和准确性？
**回答要点:**
1. **多数据源对比**: 同时使用腾讯和东方财富API
2. **数据校验**: 检查价格变动幅度是否合理
3. **异常处理**: API失败时返回缓存数据
4. **监控告警**: 数据异常时发送告警
5. **人工复核**: 关键数据提供人工复核机制

---

## 七、代码细节问题

### Q24: 解释这段代码的作用
```java
@Scheduled(fixedRate = 3000)
public void pushPriceUpdates() {
    for (WebSocketSession session : sessions) {
        List<String> symbols = getSubscribedStocks(session);
        Map<String, Object> data = fetchRealtimeData(symbols);
        session.sendMessage(new TextMessage(JSON.toJSONString(data)));
    }
}
```
**回答:**
- 使用Spring的@Scheduled注解，每3秒执行一次
- 遍历所有WebSocket会话
- 获取每个会话订阅的股票列表
- 从API获取实时数据
- 将数据转换为JSON格式发送给客户端

### Q25: 如何处理跨域问题？
**回答:**
```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(Arrays.asList("*"));
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(Arrays.asList("*"));
    configuration.setAllowCredentials(true);
    
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
```
- 使用Spring Security配置CORS
- 允许所有来源（生产环境应限制具体域名）
- 允许常见HTTP方法
- 允许携带凭证（Cookie、Token）

---

## 八、总结

这个项目展示了：
1. **全栈开发能力**: 前后端独立开发、联调
2. **架构设计能力**: 微服务、容器化、性能优化
3. **问题解决能力**: 高并发、大数据量、资源限制
4. **工程化能力**: 代码规范、Git管理、文档编写

建议面试时：
- 结合具体代码讲解
- 强调自己的贡献和思考
- 展示解决问题的能力
- 说明项目的商业价值
