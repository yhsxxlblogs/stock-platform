package com.stock.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.platform.annotation.Nullable;
import com.stock.platform.entity.StockBasic;
import com.stock.platform.repository.StockBasicRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A股股票基础信息同步服务
 * 从东方财富API或腾讯API获取所有A股股票并存储到数据库
 */
@Service
@Slf4j
public class StockBasicSyncService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private StockBasicRepository stockBasicRepository;

    // 东方财富API - 获取A股列表
    private static final String EASTMONEY_API = "https://push2.eastmoney.com/api/qt/clist/get";

    // 腾讯API - 获取股票列表（备用）
    private static final String TENCENT_API = "https://qt.gtimg.cn/q=";

    // 常用A股股票代码列表（当API不可用时使用）
    private static final String[][] COMMON_STOCKS = {
        // 上海主板 - 蓝筹股
        {"600519", "贵州茅台", "SH", "主板"},
        {"600036", "招商银行", "SH", "主板"},
        {"601318", "中国平安", "SH", "主板"},
        {"600276", "恒瑞医药", "SH", "主板"},
        {"600887", "伊利股份", "SH", "主板"},
        {"601398", "工商银行", "SH", "主板"},
        {"601288", "农业银行", "SH", "主板"},
        {"601939", "建设银行", "SH", "主板"},
        {"601988", "中国银行", "SH", "主板"},
        {"600900", "长江电力", "SH", "主板"},
        {"600030", "中信证券", "SH", "主板"},
        {"601668", "中国建筑", "SH", "主板"},
        {"601857", "中国石油", "SH", "主板"},
        {"601088", "中国神华", "SH", "主板"},
        {"601628", "中国人寿", "SH", "主板"},
        {"601728", "中国电信", "SH", "主板"},
        {"601888", "中国中免", "SH", "主板"},
        {"601899", "紫金矿业", "SH", "主板"},
        {"600309", "万华化学", "SH", "主板"},
        {"600436", "片仔癀", "SH", "主板"},
        {"600809", "山西汾酒", "SH", "主板"},
        {"603288", "海天味业", "SH", "主板"},
        {"600570", "恒生电子", "SH", "主板"},
        {"600585", "海螺水泥", "SH", "主板"},
        {"600000", "浦发银行", "SH", "主板"},
        {"600016", "民生银行", "SH", "主板"},
        {"600028", "中国石化", "SH", "主板"},
        {"600048", "保利发展", "SH", "主板"},
        {"600050", "中国联通", "SH", "主板"},
        {"600104", "上汽集团", "SH", "主板"},
        {"600111", "北方稀土", "SH", "主板"},
        {"600438", "通威股份", "SH", "主板"},
        {"600588", "用友网络", "SH", "主板"},
        {"600660", "福耀玻璃", "SH", "主板"},
        {"600745", "闻泰科技", "SH", "主板"},
        {"600837", "海通证券", "SH", "主板"},
        {"601006", "大秦铁路", "SH", "主板"},
        {"601012", "隆基绿能", "SH", "主板"},
        {"601066", "中信建投", "SH", "主板"},
        {"601111", "中国国航", "SH", "主板"},
        {"601138", "工业富联", "SH", "主板"},
        {"601166", "兴业银行", "SH", "主板"},
        {"601186", "中国铁建", "SH", "主板"},
        {"601211", "国泰君安", "SH", "主板"},
        {"601328", "交通银行", "SH", "主板"},
        {"601390", "中国中铁", "SH", "主板"},
        {"601600", "中国铝业", "SH", "主板"},
        {"601633", "长城汽车", "SH", "主板"},
        {"601688", "华泰证券", "SH", "主板"},
        {"601766", "中国中车", "SH", "主板"},
        {"601800", "中国交建", "SH", "主板"},
        {"601989", "中国重工", "SH", "主板"},
        {"603501", "韦尔股份", "SH", "主板"},
        {"603799", "华友钴业", "SH", "主板"},
        {"603986", "兆易创新", "SH", "主板"},
        {"603993", "洛阳钼业", "SH", "主板"},

        // 上海科创板
        {"688981", "中芯国际", "SH", "科创板"},
        {"688008", "澜起科技", "SH", "科创板"},
        {"688012", "中微公司", "SH", "科创板"},
        {"688036", "传音控股", "SH", "科创板"},
        {"688111", "金山办公", "SH", "科创板"},
        {"688169", "石头科技", "SH", "科创板"},
        {"688185", "康希诺", "SH", "科创板"},
        {"688599", "天合光能", "SH", "科创板"},

        // 深圳主板
        {"000001", "平安银行", "SZ", "主板"},
        {"000002", "万科A", "SZ", "主板"},
        {"000063", "中兴通讯", "SZ", "主板"},
        {"000333", "美的集团", "SZ", "主板"},
        {"000538", "云南白药", "SZ", "主板"},
        {"000568", "泸州老窖", "SZ", "主板"},
        {"000651", "格力电器", "SZ", "主板"},
        {"000725", "京东方A", "SZ", "主板"},
        {"000858", "五粮液", "SZ", "主板"},
        {"000895", "双汇发展", "SZ", "主板"},
        {"000938", "中芯国际", "SZ", "主板"},
        {"000596", "古井贡酒", "SZ", "主板"},

        // 深圳创业板
        {"300750", "宁德时代", "SZ", "创业板"},
        {"300059", "东方财富", "SZ", "创业板"},
        {"300015", "爱尔眼科", "SZ", "创业板"},
        {"300122", "智飞生物", "SZ", "创业板"},
        {"300124", "汇川技术", "SZ", "创业板"},
        {"300274", "阳光电源", "SZ", "创业板"},
        {"300014", "亿纬锂能", "SZ", "创业板"},
        {"300433", "蓝思科技", "SZ", "创业板"},
        {"300498", "温氏股份", "SZ", "创业板"},
        {"300413", "芒果超媒", "SZ", "创业板"},
        {"300003", "乐普医疗", "SZ", "创业板"},
        {"300033", "同花顺", "SZ", "创业板"},

        // 中小板（现合并到深圳主板）
        {"002001", "新和成", "SZ", "主板"},
        {"002007", "华兰生物", "SZ", "主板"},
        {"002027", "分众传媒", "SZ", "主板"},
        {"002049", "紫光国微", "SZ", "主板"},
        {"002142", "宁波银行", "SZ", "主板"},
        {"002230", "科大讯飞", "SZ", "主板"},
        {"002236", "大华股份", "SZ", "主板"},
        {"002271", "东方雨虹", "SZ", "主板"},
        {"002304", "洋河股份", "SZ", "主板"},
        {"002352", "顺丰控股", "SZ", "主板"},
        {"002415", "海康威视", "SZ", "主板"},
        {"002460", "赣锋锂业", "SZ", "主板"},
        {"002466", "天齐锂业", "SZ", "主板"},
        {"002475", "立讯精密", "SZ", "主板"},
        {"002507", "涪陵榨菜", "SZ", "主板"},
        {"002555", "三七互娱", "SZ", "主板"},
        {"002594", "比亚迪", "SZ", "主板"},
        {"002812", "恩捷股份", "SZ", "主板"},
        {"002916", "深南电路", "SZ", "主板"},
        {"002078", "太阳纸业", "SZ", "主板"},
        {"002129", "TCL中环", "SZ", "主板"},
        {"002602", "世纪华通", "SZ", "主板"},

        // 北交所
        {"835185", "贝特瑞", "BJ", "北交所"},
        {"832735", "德源药业", "BJ", "北交所"},
        {"836077", "吉林碳谷", "BJ", "北交所"},
    };

    /**
     * 应用启动时同步股票列表
     * 如果数据库中已有足够数据，跳过同步
     * 注意：已禁用自动同步，需要手动触发
     */
    // @PostConstruct - 禁用启动时自动同步
    public void init() {
        // 检查数据库中是否已有数据
        long count = stockBasicRepository.count();
        if (count < 100) {
            log.info("Database has insufficient stocks ({}), starting sync...", count);
            // 先尝试从API获取，如果失败则使用本地数据
            syncAllStocksWithRetry();
            // 如果API同步后仍然不足，使用本地数据补充
            if (stockBasicRepository.count() < 100) {
                loadLocalStocks();
            }
        } else {
            log.info("Database already has {} stocks, skip startup sync", count);
        }
    }

    /**
     * 加载本地股票数据（当API不可用时）
     */
    private void loadLocalStocks() {
        log.info("使用本地股票数据补充...");
        List<StockBasic> stocks = new ArrayList<>();

        for (String[] stockData : COMMON_STOCKS) {
            if (!stockBasicRepository.existsBySymbol(stockData[0])) {
                StockBasic stock = StockBasic.builder()
                        .symbol(stockData[0])
                        .name(stockData[1])
                        .exchange(stockData[2])
                        .marketType(stockData[3])
                        .status(1)
                        .build();
                stocks.add(stock);
            }
        }

        if (!stocks.isEmpty()) {
            saveStocksInBatches(stocks, 100);
            log.info("本地数据加载Completed，新增{}stocks", stocks.size());
        } else {
            log.info("本地股票数据已全部存在，无需Add");
        }
    }

    /**
     * 同步所有A股股票到数据库（带重试机制）
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
        log.error("{}次同步尝试均Failed，将使用本地数据", maxRetries);
    }

    /**
     * 同步所有A股股票到数据库
     */
    public void syncAllStocks() {
        try {
            log.info("Start从东方财富同步A股全市场股票列表...");

            List<StockBasic> allStocks = new ArrayList<>();

            // 上海主板 - 约2000只
            log.info("StartGet上海主板股票...");
            List<StockBasic> shMain = fetchStocksFromAPIWithRetry("m:1+t:2", "SH", "主板");
            allStocks.addAll(shMain);
            log.info("上海主板GetCompleted，共{}只", shMain.size());

            // 上海科创板 - 约500只
            log.info("StartGet上海科创板股票...");
            List<StockBasic> shKcb = fetchStocksFromAPIWithRetry("m:1+t:23", "SH", "科创板");
            allStocks.addAll(shKcb);
            log.info("上海科创板GetCompleted，共{}只", shKcb.size());

            // 深圳主板 - 约1500只
            log.info("StartGet深圳主板股票...");
            List<StockBasic> szMain = fetchStocksFromAPIWithRetry("m:0+t:6", "SZ", "主板");
            allStocks.addAll(szMain);
            log.info("深圳主板GetCompleted，共{}只", szMain.size());

            // 深圳创业板 - 约1200只
            log.info("StartGet深圳创业板股票...");
            List<StockBasic> szCy = fetchStocksFromAPIWithRetry("m:0+t:13", "SZ", "创业板");
            allStocks.addAll(szCy);
            log.info("深圳创业板GetCompleted，共{}只", szCy.size());

            // 北交所 - 约200只
            log.info("StartGet北交所股票...");
            List<StockBasic> bj = fetchStocksFromAPIWithRetry("m:0+t:81+s:204", "BJ", "北交所");
            allStocks.addAll(bj);
            log.info("北交所GetCompleted，共{}只", bj.size());

            log.info("从APIGet到{}stocks，Start保存到数据库...", allStocks.size());

            // 分批保存到数据库，每批500只
            if (!allStocks.isEmpty()) {
                saveStocksInBatches(allStocks, 500);
                log.info("股票列表Sync completed，数据库中共有{}stocks", stockBasicRepository.count());
            }

        } catch (Exception e) {
            log.error("同步A股全市场股票列表Failed: {}", e.getMessage(), e);
            throw e;
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
     */
    private List<StockBasic> fetchStocksFromAPI(String fs, String exchange, String marketType) {
        List<StockBasic> stocks = new ArrayList<>();
        int page = 1;
        int pageSize = 1000;
        int maxPages = 10; // 每个市场最多10页
        int emptyPageCount = 0; // 连续空页计数

        while (page <= maxPages && emptyPageCount < 3) {
            try {
                // 简化API调用，只获取代码和名称
                String url = String.format("%s?pn=%d&pz=%d&po=1&np=1&fltt=2&invt=2&fid=f12&fs=%s&fields=f12,f14",
                        EASTMONEY_API, page, pageSize, fs);

                log.debug("请求URL: {}", url);

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
                        emptyPageCount = 0; // 重置空页计数
                        stocks.addAll(pageStocks);
                        log.info("Get{} {}第{}页，本页{}只，累计{}只", exchange, marketType, page, pageStocks.size(), stocks.size());
                    }
                    page++;
                } else {
                    emptyPageCount++;
                    log.warn("{} {} 第{}页响应为空", exchange, marketType, page);
                }

                // 避免请求过快
                TimeUnit.MILLISECONDS.sleep(300);

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

            // data.diff 可能是对象或数组，需要兼容处理
            if (dataNode.isArray()) {
                // 如果是数组，直接遍历数组元素
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
                // 如果是对象，遍历字段
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
                    if (failed <= 10) { // 只记录前10个错误
                        log.warn("保存股票{}Failed: {}", stock.getSymbol(), e.getMessage());
                    }
                }
            }

            log.info("已Process {}/{} stocks (新增:{}, Update:{}, Failed:{})",
                    Math.min(i + batchSize, total), total, saved, updated, failed);

            // 每批处理后短暂休息
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.info("保存Completed：新增{}只，Update{}只，Failed{}只", saved, updated, failed);
    }

    /**
     * 搜索股票
     */
    public List<StockBasic> searchStocks(String keyword, int limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<StockBasic> results = stockBasicRepository.searchByKeyword(keyword.trim());
        if (results.size() > limit) {
            return results.subList(0, limit);
        }
        return results;
    }

    /**
     * 根据代码获取股票
     */
    @Nullable
    public StockBasic getStockBySymbol(String symbol) {
        return stockBasicRepository.findBySymbol(symbol).orElse(null);
    }

    /**
     * 获取所有股票数量
     */
    public long getStockCount() {
        return stockBasicRepository.count();
    }

    /**
     * 定时同步股票列表 - 每天凌晨2点执行
     */
    public void scheduledSync() {
        log.info("Start定时同步股票列表...");
        try {
            // 获取同步前的股票数量
            long beforeCount = stockBasicRepository.count();
            log.info("同步前数据库中有{}stocks", beforeCount);

            // 执行同步（带重试）
            syncAllStocksWithRetry();

            // 如果API同步后仍然不足，使用本地数据补充
            if (stockBasicRepository.count() < 100) {
                loadLocalStocks();
            }

            // 获取同步后的股票数量
            long afterCount = stockBasicRepository.count();
            log.info("同步后数据库中有{}stocks", afterCount);

            // 检查是否有新增股票
            if (afterCount > beforeCount) {
                log.info("本次同步新增{}stocks", afterCount - beforeCount);
            }

        } catch (Exception e) {
            log.error("定时同步股票列表Failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 增量同步 - 只同步新增股票
     */
    public void incrementalSync() {
        log.info("Start增量同步股票列表...");
        try {
            // 获取现有股票代码集合
            List<String> existingSymbols = stockBasicRepository.findByStatus(1).stream()
                    .map(StockBasic::getSymbol)
                    .toList();
            log.info("数据库中已有{}stocks", existingSymbols.size());

            // 获取所有市场的新股票
            List<StockBasic> newStocks = new ArrayList<>();

            // 遍历所有市场
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
                saveStocksInBatches(newStocks, 500);
                log.info("增量Sync completed，新增{}stocks", newStocks.size());
            } else {
                log.info("没有新股票需要同步");
            }

        } catch (Exception e) {
            log.error("增量同步Failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 手动触发本地数据加载
     */
    public void loadLocalStockData() {
        log.info("手动触发本地股票数据加载...");
        loadLocalStocks();
    }
}
