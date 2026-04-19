package com.stock.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.platform.entity.Stock;
import com.stock.platform.entity.StockBasic;
import com.stock.platform.repository.StockBasicRepository;
import com.stock.platform.repository.StockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 股票数据初始化服务
 * 容器启动时检查数据库股票数据，如不足则从本地JSON文件同步
 */
@Service
@Slf4j
public class StockDataInitializer {

    @Autowired
    private StockBasicRepository stockBasicRepository;

    @Autowired
    private StockRepository stockRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 最小股票数量阈值
    private static final int MIN_STOCK_COUNT = 500;
    private static final int TARGET_STOCK_COUNT = 5000;

    /**
     * 应用启动时执行数据初始化检查
     */
    @PostConstruct
    public void init() {
        log.info("==============================================");
        log.info("开始检查股票数据完整性...");
        log.info("==============================================");

        // 延迟5秒执行，等待数据库连接就绪
        new Thread(() -> {
            try {
                TimeUnit.SECONDS.sleep(5);
                checkAndInitializeStockData();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("数据初始化线程被中断", e);
            }
        }).start();
    }

    /**
     * 检查并初始化股票数据
     */
    @Transactional
    public void checkAndInitializeStockData() {
        try {
            long currentCount = stockBasicRepository.count();
            log.info("当前数据库中股票数量: {}", currentCount);

            if (currentCount < MIN_STOCK_COUNT) {
                log.warn("股票数据不足 ({} < {})，开始从本地数据文件同步...", currentCount, MIN_STOCK_COUNT);
                loadStockDataFromLocalFile();

                // 再次检查
                long afterCount = stockBasicRepository.count();
                log.info("同步完成后数据库中股票数量: {}", afterCount);

                if (afterCount >= MIN_STOCK_COUNT) {
                    log.info("✅ 股票数据同步成功！");
                } else {
                    log.error("❌ 股票数据同步后仍不足，请检查数据文件");
                }
            } else if (currentCount < TARGET_STOCK_COUNT) {
                log.info("股票数量充足 ({} >= {})，但未满额，执行增量同步...", currentCount, MIN_STOCK_COUNT);
                loadStockDataFromLocalFile();
                log.info("增量同步完成，当前股票数量: {}", stockBasicRepository.count());
            } else {
                log.info("✅ 股票数据已充足 ({} >= {})，跳过同步", currentCount, TARGET_STOCK_COUNT);
            }

        } catch (Exception e) {
            log.error("股票数据初始化失败: {}", e.getMessage(), e);
        }

        log.info("==============================================");
        log.info("股票数据检查完成");
        log.info("==============================================");
    }

    /**
     * 从本地JSON文件加载股票数据
     */
    @Transactional
    public void loadStockDataFromLocalFile() {
        try {
            log.info("开始加载本地股票数据文件...");

            // 读取JSON文件
            ClassPathResource resource = new ClassPathResource("stocks-data.json");
            JsonNode rootNode = objectMapper.readTree(resource.getInputStream());

            // 获取版本信息
            String version = rootNode.path("version").asText();
            int totalCount = rootNode.path("totalCount").asInt();
            String lastUpdated = rootNode.path("lastUpdated").asText();

            log.info("数据文件版本: {}, 总股票数: {}, 更新日期: {}", version, totalCount, lastUpdated);

            // 解析各市场数据
            List<StockBasic> allStocks = new ArrayList<>();
            JsonNode marketsNode = rootNode.path("markets");

            Iterator<String> marketNames = marketsNode.fieldNames();
            while (marketNames.hasNext()) {
                String marketKey = marketNames.next();
                JsonNode marketNode = marketsNode.path(marketKey);

                String marketName = marketNode.path("name").asText();
                JsonNode stocksNode = marketNode.path("stocks");

                log.info("正在加载 {} 数据，共 {} 只股票...", marketName, stocksNode.size());

                for (JsonNode stockNode : stocksNode) {
                    StockBasic stock = parseStockFromJson(stockNode, marketKey);
                    if (stock != null) {
                        allStocks.add(stock);
                    }
                }
            }

            log.info("从文件解析到 {} 只股票，开始保存到数据库...", allStocks.size());

            // 分批保存
            saveStocksInBatches(allStocks, 100);

            // 同步到Stock表
            syncToStockTable(allStocks);

            log.info("本地股票数据加载完成！");

        } catch (Exception e) {
            log.error("加载本地股票数据失败: {}", e.getMessage(), e);
            throw new RuntimeException("加载股票数据失败", e);
        }
    }

    /**
     * 从JSON解析股票信息
     */
    private StockBasic parseStockFromJson(JsonNode stockNode, String marketKey) {
        try {
            String symbol = stockNode.path("symbol").asText();
            String name = stockNode.path("name").asText();
            String industry = stockNode.path("industry").asText();

            if (symbol.isEmpty() || name.isEmpty()) {
                return null;
            }

            // 根据市场代码确定交易所
            String exchange;
            String marketType;

            switch (marketKey) {
                case "SH_MAIN":
                    exchange = "SH";
                    marketType = symbol.startsWith("688") ? "科创板" : "主板";
                    break;
                case "SZ_MAIN":
                    exchange = "SZ";
                    if (symbol.startsWith("300") || symbol.startsWith("301")) {
                        marketType = "创业板";
                    } else {
                        marketType = "主板";
                    }
                    break;
                case "BJ":
                    exchange = "BJ";
                    marketType = "北交所";
                    break;
                default:
                    exchange = "SH";
                    marketType = "主板";
            }

            return StockBasic.builder()
                    .symbol(symbol)
                    .name(name)
                    .exchange(exchange)
                    .marketType(marketType)
                    .industry(industry)
                    .status(1)
                    .build();

        } catch (Exception e) {
            log.warn("解析股票数据失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 分批保存股票数据
     */
    private void saveStocksInBatches(List<StockBasic> stocks, int batchSize) {
        int total = stocks.size();
        int saved = 0;
        int updated = 0;
        int skipped = 0;

        for (int i = 0; i < total; i += batchSize) {
            List<StockBasic> batch = stocks.subList(i, Math.min(i + batchSize, total));

            for (StockBasic stock : batch) {
                try {
                    if (stockBasicRepository.existsBySymbol(stock.getSymbol())) {
                        // 更新现有记录
                        StockBasic existing = stockBasicRepository.findBySymbol(stock.getSymbol()).orElse(null);
                        if (existing != null) {
                            existing.setName(stock.getName());
                            existing.setExchange(stock.getExchange());
                            existing.setMarketType(stock.getMarketType());
                            existing.setIndustry(stock.getIndustry());
                            existing.setStatus(1);
                            existing.setUpdatedAt(LocalDateTime.now());
                            stockBasicRepository.save(existing);
                            updated++;
                        }
                    } else {
                        // 插入新记录
                        stockBasicRepository.save(stock);
                        saved++;
                    }
                } catch (Exception e) {
                    skipped++;
                    if (skipped <= 10) {
                        log.warn("保存股票 {} 失败: {}", stock.getSymbol(), e.getMessage());
                    }
                }
            }

            if ((i / batchSize + 1) % 10 == 0 || i + batchSize >= total) {
                log.info("已处理 {}/{} 只股票 (新增:{}, 更新:{}, 跳过:{})",
                        Math.min(i + batchSize, total), total, saved, updated, skipped);
            }
        }

        log.info("批量保存完成：新增 {} 只，更新 {} 只，跳过 {} 只", saved, updated, skipped);
    }

    /**
     * 同步到Stock表
     */
    @Transactional
    public void syncToStockTable(List<StockBasic> stockBasics) {
        log.info("开始同步到Stock表...");

        int saved = 0;
        int skipped = 0;

        for (StockBasic basic : stockBasics) {
            try {
                // 检查是否已存在
                if (!stockRepository.existsBySymbol(basic.getSymbol())) {
                    Stock stock = new Stock();
                    stock.setSymbol(basic.getSymbol());
                    stock.setName(basic.getName());
                    stock.setExchange(basic.getExchange());
                    stock.setIndustry(basic.getIndustry());
                    stock.setStatus(1);
                    stock.setCreatedAt(LocalDateTime.now());
                    stock.setUpdatedAt(LocalDateTime.now());

                    stockRepository.save(stock);
                    saved++;

                    if (saved % 100 == 0) {
                        log.info("已同步 {} 只股票到Stock表", saved);
                    }
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.warn("同步股票 {} 到Stock表失败: {}", basic.getSymbol(), e.getMessage());
            }
        }

        log.info("Stock表同步完成：新增 {} 只，跳过 {} 只", saved, skipped);
    }
}
