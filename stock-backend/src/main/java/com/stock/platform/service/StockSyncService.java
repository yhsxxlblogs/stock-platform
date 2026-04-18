package com.stock.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.platform.entity.Stock;
import com.stock.platform.entity.StockBasic;
import com.stock.platform.repository.StockBasicRepository;
import com.stock.platform.repository.StockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 增强版股票同步服务
 * 支持分批获取所有A股(5000+只)，定时检查和自动添加新股票
 */
@Service
@Slf4j
public class StockSyncService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockBasicRepository stockBasicRepository;

    // 东方财富API - 获取A股列表
    private static final String EASTMONEY_API = "https://push2.eastmoney.com/api/qt/clist/get";

    // 腾讯API - 获取股票详细信息
    private static final String TENCENT_API = "https://qt.gtimg.cn/q=";

    // 批量大小配置
    private static final int BATCH_SIZE = 500;           // 每批处理500只
    private static final int API_BATCH_SIZE = 800;       // API每批请求800只
    private static final int THREAD_POOL_SIZE = 4;       // 线程池大小

    // 线程池用于并行处理
    private final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    /**
     * 应用启动时执行初始同步
     * 注意：已禁用自动同步，需要手动触发
     */
    // @PostConstruct - 禁用启动时自动同步
    public void init() {
        // 延迟10秒执行，等待应用完全启动
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(10000);
                checkAndSyncStocks();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * 检查并同步股票列表
     * 如果数据库中股票数量不足5000只，则执行全量同步
     */
    @Transactional
    public void checkAndSyncStocks() {
        long count = stockBasicRepository.count();
        log.info("当前数据库中股票数量: {}", count);

        if (count < 5000) {
            log.info("股票数量不足5000只，Start全量同步...");
            syncAllStocksWithRetry();
        } else {
            log.info("股票数量充足，Execute增量同步检查...");
            incrementalSync();
        }
    }

    /**
     * 全量同步所有A股股票（带重试机制）
     */
    public void syncAllStocksWithRetry() {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                log.info("第{}次尝试同步A股全市场股票列表...", i + 1);
                syncAllStocks();

                // 检查同步结果
                long count = stockBasicRepository.count();
                if (count >= 5000) {
                    log.info("同步Success！数据库中共有{}stocks", count);
                    // 同步到Stock表
                    syncToStockTable();
                    return;
                } else {
                    log.warn("同步后只有{}stocks，未达到5000只目标", count);
                }
            } catch (Exception e) {
                log.error("第{}次同步Failed: {}", i + 1, e.getMessage());
                if (i < maxRetries - 1) {
                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        log.error("{}次同步尝试均Failed", maxRetries);
    }

    /**
     * 同步所有A股股票到数据库
     * 分批获取，避免API限制
     */
    public void syncAllStocks() {
        log.info("Start从东方财富同步A股全市场股票列表...");

        List<StockBasic> allStocks = new ArrayList<>();

        // 定义所有市场
        String[][] markets = {
                {"m:1+t:2", "SH", "主板"},        // 上海主板
                {"m:1+t:23", "SH", "科创板"},     // 上海科创板
                {"m:0+t:6", "SZ", "主板"},        // 深圳主板
                {"m:0+t:13", "SZ", "创业板"},     // 深圳创业板
                {"m:0+t:81+s:204", "BJ", "北交所"} // 北交所
        };

        // 使用并行流加速获取
        List<CompletableFuture<List<StockBasic>>> futures = Arrays.stream(markets)
                .map(market -> CompletableFuture.supplyAsync(() -> {
                    log.info("StartGet{}{}股票...", market[1], market[2]);
                    List<StockBasic> stocks = fetchStocksFromAPIWithRetry(market[0], market[1], market[2]);
                    log.info("{}{}GetCompleted，共{}只", market[1], market[2], stocks.size());
                    return stocks;
                }, executorService))
                .toList();

        // 等待所有任务完成
        @SuppressWarnings("null")
        CompletableFuture<List<StockBasic>>[] futureArray = futures.toArray(new CompletableFuture[0]);
        CompletableFuture.allOf(futureArray).join();

        // 收集结果
        for (CompletableFuture<List<StockBasic>> future : futures) {
            try {
                allStocks.addAll(future.get());
            } catch (Exception e) {
                log.error("Get股票列表Failed: {}", e.getMessage());
            }
        }

        log.info("从APIGet到{}stocks，Start保存到数据库...", allStocks.size());

        // 分批保存到数据库
        if (!allStocks.isEmpty()) {
            saveStocksInBatches(allStocks, BATCH_SIZE);
            log.info("股票列表Sync completed，数据库中共有{}stocks", stockBasicRepository.count());
        }
    }

    /**
     * 从API获取股票列表（带重试）
     */
    private List<StockBasic> fetchStocksFromAPIWithRetry(String fs, String exchange, String marketType) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                List<StockBasic> stocks = fetchStocksFromAPI(fs, exchange, marketType);
                if (!stocks.isEmpty()) {
                    return stocks;
                }
                log.warn("{} {} 第{}次尝试Get为空，重试...", exchange, marketType, i + 1);
                TimeUnit.SECONDS.sleep(2);
            } catch (Exception e) {
                log.error("{} {} 第{}次尝试Failed: {}", exchange, marketType, i + 1, e.getMessage());
                if (i < maxRetries - 1) {
                    try {
                        TimeUnit.SECONDS.sleep(3);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        return new ArrayList<>();
    }

    /**
     * 从API获取股票列表
     * 自动分页获取所有数据
     */
    private List<StockBasic> fetchStocksFromAPI(String fs, String exchange, String marketType) {
        List<StockBasic> stocks = new ArrayList<>();
        int page = 1;
        int pageSize = 1000;
        int maxPages = 20; // 每个市场最多20页
        int emptyPageCount = 0;

        while (page <= maxPages && emptyPageCount < 3) {
            try {
                String url = String.format("%s?pn=%d&pz=%d&po=1&np=1&fltt=2&invt=2&fid=f12&fs=%s&fields=f12,f14",
                        EASTMONEY_API, page, pageSize, fs);

                HttpHeaders headers = new HttpHeaders();
                headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                headers.set("Accept", "application/json");

                HttpEntity<String> entity = new HttpEntity<>(headers);
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);

                if (response.getBody() != null) {
                    List<StockBasic> pageStocks = parseStocks(response.getBody(), exchange, marketType);

                    if (pageStocks.isEmpty()) {
                        emptyPageCount++;
                        log.debug("{} {} 第{}页无数据，连续空页{}次", exchange, marketType, page, emptyPageCount);
                    } else {
                        emptyPageCount = 0;
                        stocks.addAll(pageStocks);
                        log.debug("Get{} {}第{}页，本页{}只，累计{}只", exchange, marketType, page, pageStocks.size(), stocks.size());
                    }
                    page++;
                } else {
                    emptyPageCount++;
                }

                // 避免请求过快
                TimeUnit.MILLISECONDS.sleep(200);

            } catch (Exception e) {
                log.error("Get{} {}第{}页Failed: {}", exchange, marketType, page, e.getMessage());
                emptyPageCount++;
                if (emptyPageCount >= 3) {
                    break;
                }
            }
        }

        return stocks;
    }

    /**
     * 解析API返回的股票数据
     */
    private List<StockBasic> parseStocks(String jsonData, String exchange, String marketType) {
        List<StockBasic> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonData);
            JsonNode dataNode = root.path("data").path("diff");

            if (dataNode.isArray()) {
                for (JsonNode stockNode : dataNode) {
                    String code = stockNode.path("f12").asText();
                    String name = stockNode.path("f14").asText();

                    if (!code.isEmpty() && !name.isEmpty()) {
                        StockBasic stock = StockBasic.builder()
                                .symbol(code)
                                .name(name)
                                .exchange(exchange)
                                .marketType(marketType)
                                .status(1)
                                .build();
                        result.add(stock);
                    }
                }
            } else if (dataNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = dataNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    JsonNode stockNode = entry.getValue();

                    String code = stockNode.path("f12").asText();
                    String name = stockNode.path("f14").asText();

                    if (!code.isEmpty() && !name.isEmpty()) {
                        StockBasic stock = StockBasic.builder()
                                .symbol(code)
                                .name(name)
                                .exchange(exchange)
                                .marketType(marketType)
                                .status(1)
                                .build();
                        result.add(stock);
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析股票列表Failed: {}", e.getMessage(), e);
        }
        return result;
    }

    /**
     * 分批保存股票信息
     */
    private void saveStocksInBatches(List<StockBasic> stocks, int batchSize) {
        int total = stocks.size();
        int saved = 0;
        int updated = 0;
        int failed = 0;

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
                    failed++;
                    if (failed <= 10) {
                        log.warn("保存股票{}Failed: {}", stock.getSymbol(), e.getMessage());
                    }
                }
            }

            if ((i / batchSize + 1) % 5 == 0 || i + batchSize >= total) {
                log.info("已Process {}/{} stocks (新增:{}, Update:{}, Failed:{})",
                        Math.min(i + batchSize, total), total, saved, updated, failed);
            }

            // 每批处理后短暂休息
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.info("保存Completed：新增{}只，Update{}只，Failed{}只", saved, updated, failed);
    }

    /**
     * 同步StockBasic到Stock表
     */
    @Transactional
    public void syncToStockTable() {
        log.info("Start同步StockBasic到Stock表...");

        List<StockBasic> allBasicStocks = stockBasicRepository.findByStatus(1);
        List<Stock> existingStocks = stockRepository.findByStatus(1);
        Set<String> existingSymbols = existingStocks.stream()
                .map(Stock::getSymbol)
                .collect(Collectors.toSet());

        int saved = 0;
        int skipped = 0;

        for (StockBasic basic : allBasicStocks) {
            try {
                if (!existingSymbols.contains(basic.getSymbol())) {
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
                        log.info("已同步{}只新股票到Stock表", saved);
                    }
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.warn("同步股票{}到Stock表Failed: {}", basic.getSymbol(), e.getMessage());
            }
        }

        log.info("Stock表Sync completed：新增{}只，Skip{}只", saved, skipped);
    }

    /**
     * 增量同步 - 只同步新增股票
     * 每天凌晨2点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void incrementalSync() {
        log.info("Start增量同步股票列表...");
        try {
            // 获取现有股票代码集合
            Set<String> existingSymbols = stockBasicRepository.findByStatus(1).stream()
                    .map(StockBasic::getSymbol)
                    .collect(Collectors.toSet());
            log.info("数据库中已有{}stocks", existingSymbols.size());

            // 获取所有市场的新股票
            List<StockBasic> newStocks = new ArrayList<>();

            String[][] markets = {
                    {"m:1+t:2", "SH", "主板"},
                    {"m:1+t:23", "SH", "科创板"},
                    {"m:0+t:6", "SZ", "主板"},
                    {"m:0+t:13", "SZ", "创业板"},
                    {"m:0+t:81+s:204", "BJ", "北交所"}
            };

            for (String[] market : markets) {
                List<StockBasic> marketStocks = fetchStocksFromAPIWithRetry(market[0], market[1], market[2]);
                for (StockBasic stock : marketStocks) {
                    if (!existingSymbols.contains(stock.getSymbol())) {
                        newStocks.add(stock);
                    }
                }
            }

            // 保存新股票
            if (!newStocks.isEmpty()) {
                log.info("发现{}只新股票，Start保存...", newStocks.size());
                saveStocksInBatches(newStocks, BATCH_SIZE);
                // 同步到Stock表
                syncNewStocksToStockTable(newStocks);
                log.info("增量Sync completed，新增{}stocks", newStocks.size());
            } else {
                log.info("没有新股票需要同步");
            }

        } catch (Exception e) {
            log.error("增量同步Failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 定时全量检查同步 - 每周日凌晨3点执行
     * 用于修复可能的数据不一致问题
     */
    @Scheduled(cron = "0 0 3 ? * SUN")
    @Transactional
    public void scheduledFullSync() {
        log.info("Start定时全量同步股票列表...");
        try {
            long beforeCount = stockBasicRepository.count();
            log.info("同步前数据库中有{}stocks", beforeCount);

            // 执行全量同步
            syncAllStocksWithRetry();

            long afterCount = stockBasicRepository.count();
            log.info("同步后数据库中有{}stocks", afterCount);

            if (afterCount > beforeCount) {
                log.info("本次同步新增{}stocks", afterCount - beforeCount);
            }

        } catch (Exception e) {
            log.error("定时全量同步Failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 同步新股票到Stock表
     */
    private void syncNewStocksToStockTable(List<StockBasic> newStocks) {
        log.info("Start同步{}只新股票到Stock表...", newStocks.size());
        int saved = 0;

        for (StockBasic basic : newStocks) {
            try {
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
            } catch (Exception e) {
                log.warn("同步股票{}到Stock表Failed: {}", basic.getSymbol(), e.getMessage());
            }
        }

        log.info("Stock表Sync completed：新增{}只", saved);
    }

    /**
     * 手动触发全量同步（供管理员使用）
     */
    public void manualFullSync() {
        log.info("手动触发全量同步...");
        syncAllStocksWithRetry();
    }

    /**
     * 获取同步状态
     */
    public Map<String, Object> getSyncStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("stockBasicCount", stockBasicRepository.count());
        status.put("stockCount", stockRepository.count());
        status.put("lastSyncTime", LocalDateTime.now());
        status.put("syncStatus", stockBasicRepository.count() >= 5000 ? "NORMAL" : "INSUFFICIENT");
        return status;
    }

    /**
     * 销毁时关闭线程池
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
