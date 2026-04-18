-- ==========================================
-- 股票数据同步脚本
-- 将 stock_basic 表的数据同步到 stocks 表
-- 只同步不存在的股票
-- ==========================================

-- 同步数据
INSERT INTO stocks (symbol, name, exchange, industry, status, created_at, updated_at)
SELECT 
    sb.symbol,
    sb.name,
    sb.exchange,
    sb.industry,
    1 as status,
    NOW() as created_at,
    NOW() as updated_at
FROM stock_basic sb
LEFT JOIN stocks s ON sb.symbol = s.symbol
WHERE s.id IS NULL;

-- 查看同步后的数量
SELECT CONCAT('同步完成，stocks 表现有 ', COUNT(*), ' 条记录') as result FROM stocks;
