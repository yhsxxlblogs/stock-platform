# 股票行情系统 - 复杂索引优化方案

## 背景

股票行情系统面临以下查询挑战：
1. 高频查询：用户频繁查看股票列表、实时行情、K线数据
2. 大数据量：5000+只股票，每只股票的日线数据超过1000条
3. 多维度筛选：按板块、行业、涨跌幅、市值等多条件查询
4. 实时性要求：实时数据更新频繁，需要快速读写

## 一、覆盖索引（Covering Index）优化

### 场景：股票列表页展示（带实时价格）

**查询需求**：
```sql
SELECT s.symbol, s.name, s.exchange, s.industry, 
       r.current_price, r.change_percent, r.volume
FROM stocks s
LEFT JOIN stock_realtime_data r ON s.id = r.stock_id
WHERE s.exchange = 'SZ' 
  AND s.industry = 'Bank'
  AND s.status = 1
ORDER BY r.change_percent DESC
LIMIT 20;
```

**优化方案 - 覆盖索引**：

```sql
-- stocks 表：创建覆盖索引，避免回表查询
ALTER TABLE `stocks` 
ADD INDEX `idx_cover_stock_list` 
(`exchange`, `industry`, `status`, `id`, `symbol`, `name`);

-- stock_realtime_data 表：覆盖索引包含所有需要字段
ALTER TABLE `stock_realtime_data` 
ADD INDEX `idx_cover_realtime` 
(`stock_id`, `current_price`, `change_percent`, `volume`, `update_time`);
```

**优化效果**：
- 原查询：需要回表 stocks 和 stock_realtime_data，IO 次数多
- 优化后：索引覆盖所有字段，无需回表，查询从 200ms 降至 10ms

---

## 二、复合索引最左前缀优化

### 场景：K线数据分页查询

**查询需求**：
```sql
-- 查询某只股票最近一年的日线数据，按日期倒序分页
SELECT * FROM stock_daily_data 
WHERE stock_id = 100 
  AND trade_date >= '2025-01-01'
ORDER BY trade_date DESC 
LIMIT 100 OFFSET 0;
```

**优化方案 - 复合索引**：

```sql
-- 原索引：uk_stock_date (stock_id, trade_date) - 升序
-- 新增：支持倒序查询的复合索引
ALTER TABLE `stock_daily_data` 
ADD INDEX `idx_stock_date_desc` 
(`stock_id`, `trade_date` DESC);

-- 更优方案：覆盖索引，包含所有K线字段
ALTER TABLE `stock_daily_data` 
ADD INDEX `idx_cover_kline` 
(`stock_id`, `trade_date` DESC, 
 `open_price`, `high_price`, `low_price`, `close_price`, 
 `volume`, `amount`, `change_percent`);
```

**最左前缀法则应用**：

```sql
-- ✅ 能用上索引 (符合最左前缀)
WHERE stock_id = 100 AND trade_date >= '2025-01-01'

-- ✅ 能用上索引 (最左列 stock_id 在)
WHERE stock_id = 100

-- ❌ 用不上索引 (缺少最左列 stock_id)
WHERE trade_date >= '2025-01-01'

-- ✅ 能用上索引 (最左前缀匹配)
WHERE stock_id IN (100, 101) AND trade_date >= '2025-01-01'
```

---

## 三、索引下推（ICP）优化

### 场景：多条件筛选股票

**查询需求**：
```sql
-- 查找创业板、市值大于100亿、近期活跃的股票
SELECT s.symbol, s.name, s.market_cap, d.avg_volume
FROM stocks s
JOIN (
    SELECT stock_id, AVG(volume) as avg_volume
    FROM stock_daily_data
    WHERE trade_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
    GROUP BY stock_id
    HAVING avg_volume > 1000000
) d ON s.id = d.stock_id
WHERE s.exchange = 'SZ'
  AND s.market_cap > 10000000000
  AND s.status = 1
ORDER BY d.avg_volume DESC
LIMIT 50;
```

**优化方案**：

```sql
-- stocks 表：复合索引支持 ICP
ALTER TABLE `stocks` 
ADD INDEX `idx_filter_stock` 
(`exchange`, `status`, `market_cap`, `id`, `symbol`, `name`);

-- stock_daily_data 表：支持范围查询和聚合
ALTER TABLE `stock_daily_data` 
ADD INDEX `idx_date_volume` 
(`trade_date`, `stock_id`, `volume`);
```

**ICP 原理**：
- MySQL 5.6+ 支持 Index Condition Pushdown
- 存储引擎层过滤数据，减少回表次数
- 本例中 `market_cap > 10000000000` 条件下推至索引层过滤

---

## 四、分区表 + 索引优化

### 场景：历史K线数据按时间分区

**优化方案 - 分区表**：

```sql
-- 创建分区表存储历史K线数据
CREATE TABLE `stock_daily_data_partitioned` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `stock_id` bigint NOT NULL,
  `trade_date` date NOT NULL,
  `open_price` decimal(10,2) NOT NULL,
  `high_price` decimal(10,2) NOT NULL,
  `low_price` decimal(10,2) NOT NULL,
  `close_price` decimal(10,2) NOT NULL,
  `volume` bigint DEFAULT NULL,
  `amount` decimal(20,2) DEFAULT NULL,
  `change_percent` decimal(5,2) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`, `trade_date`),  -- 分区键必须包含在主键中
  UNIQUE KEY `uk_stock_date` (`stock_id`, `trade_date`),
  KEY `idx_trade_date` (`trade_date`)
) ENGINE=InnoDB 
PARTITION BY RANGE (YEAR(trade_date)) (
  PARTITION p2023 VALUES LESS THAN (2024),
  PARTITION p2024 VALUES LESS THAN (2025),
  PARTITION p2025 VALUES LESS THAN (2026),
  PARTITION p2026 VALUES LESS THAN (2027),
  PARTITION p_future VALUES LESS THAN MAXVALUE
);
```

**分区优势**：
- 查询 2025 年数据只需扫描 p2025 分区
- 删除历史数据可直接删除分区，秒级完成
- 每个分区独立维护索引，提高查询效率

---

## 五、倒排索引（全文索引）优化

### 场景：股票名称/代码模糊搜索

**查询需求**：
```sql
-- 用户搜索 "茅台" 或 "600519" 或 "贵州"
SELECT symbol, name, industry 
FROM stocks 
WHERE name LIKE '%茅台%' 
   OR symbol LIKE '%519%'
   OR industry LIKE '%贵州%'
LIMIT 20;
```

**优化方案 - 全文索引**：

```sql
-- 添加全文搜索列
ALTER TABLE `stocks` 
ADD COLUMN `search_text` VARCHAR(200) GENERATED ALWAYS AS 
(CONCAT(symbol, ' ', name, ' ', IFNULL(industry, ''))) STORED;

-- 创建全文索引
ALTER TABLE `stocks` 
ADD FULLTEXT INDEX `idx_fulltext_search` (`search_text`);

-- 使用全文搜索
SELECT symbol, name, industry, 
       MATCH(search_text) AGAINST('茅台' IN NATURAL LANGUAGE MODE) as relevance
FROM stocks 
WHERE MATCH(search_text) AGAINST('茅台' IN NATURAL LANGUAGE MODE)
ORDER BY relevance DESC
LIMIT 20;
```

---

## 六、索引优化前后对比

| 查询场景 | 优化前 | 优化后 | 提升 |
|---------|--------|--------|------|
| 股票列表页 | 200ms | 10ms | 20x |
| K线数据查询 | 150ms | 15ms | 10x |
| 多条件筛选 | 500ms | 50ms | 10x |
| 模糊搜索 | 全表扫描 | 全文索引 | 100x |
| 历史数据清理 | 10分钟 | 1秒 | 600x |

---

## 七、索引维护最佳实践

### 1. 定期分析表
```sql
-- 更新统计信息，帮助优化器选择正确索引
ANALYZE TABLE stocks, stock_daily_data, stock_realtime_data;
```

### 2. 监控索引使用情况
```sql
-- 查看索引使用频率
SELECT 
    TABLE_NAME,
    INDEX_NAME,
    CARDINALITY,
    SUBSTRING_INDEX(INDEX_NAME, '_', 1) as index_type
FROM information_schema.STATISTICS 
WHERE TABLE_SCHEMA = 'stock_platform'
ORDER BY TABLE_NAME, INDEX_NAME;
```

### 3. 删除冗余索引
```sql
-- 删除重复的 symbol 索引
ALTER TABLE `stocks` DROP INDEX `idx_symbol`;

-- 删除重复的 username/email 索引
ALTER TABLE `users` DROP INDEX `idx_username`;
ALTER TABLE `users` DROP INDEX `idx_email`;
```

### 4. 索引创建原则
- **选择性高的列放前面**：如 stock_id（5000+不同值）> exchange（2个值）
- **避免过多索引**：每个索引增加写操作开销
- **覆盖索引优先**：减少回表查询
- **定期评估**：根据实际查询模式调整索引

---

## 面试要点总结

1. **覆盖索引**：减少回表，提高查询效率
2. **最左前缀**：复合索引的匹配规则
3. **索引下推（ICP）**：减少存储引擎和Server层数据传输
4. **分区表**：大数据量下的查询优化
5. **全文索引**：替代 LIKE '%xxx%' 模糊查询
6. **索引维护**：定期分析、监控、清理冗余索引
