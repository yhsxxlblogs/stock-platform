package com.stock.platform.controller;

import com.stock.platform.dto.*;
import com.stock.platform.entity.Stock;
import com.stock.platform.entity.StockBasic;
import com.stock.platform.repository.StockBasicRepository;
import com.stock.platform.service.EastMoneyStockService;
import com.stock.platform.service.StockCacheService;
import com.stock.platform.service.StockDataService;
import com.stock.platform.service.StockSyncService;
import com.stock.platform.service.TencentStockDataService;
import com.stock.platform.util.MarketTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/stocks")
@Slf4j
public class StockController {

    @Autowired
    private StockDataService stockDataService;

    @Autowired
    private TencentStockDataService tencentStockDataService;

    @Autowired
    private EastMoneyStockService eastMoneyStockService;

    @Autowired
    private StockSyncService stockSyncService;

    @Autowired
    private StockCacheService stockCacheService;

    @Autowired
    private StockBasicRepository stockBasicRepository;

    @GetMapping("/public/list")
    public ResponseEntity<ApiResponse<List<StockDTO>>> getAllStocks() {
        List<StockDTO> stocks = stockDataService.getAllStocks();
        return ResponseEntity.ok(ApiResponse.success(stocks));
    }

    @GetMapping("/public/{symbol}")
    public ResponseEntity<ApiResponse<StockDTO>> getStockBySymbol(@PathVariable String symbol) {
        // 从数据库查找
        StockBasic stockBasic = stockBasicRepository.findBySymbol(symbol).orElse(null);

        if (stockBasic != null) {
            // 从腾讯API获取实时数据
            var realtimeInfo = tencentStockDataService.getStockFullInfo(symbol);

            StockDTO dto = new StockDTO();
            dto.setId((long) symbol.hashCode());
            dto.setSymbol(stockBasic.getSymbol());
            dto.setName(stockBasic.getName());
            dto.setExchange(stockBasic.getExchange());

            if (realtimeInfo != null && realtimeInfo.getCurrentPrice() != null) {
                dto.setCurrentPrice(realtimeInfo.getCurrentPrice());
                dto.setChangePrice(realtimeInfo.getChangePrice());
                dto.setChangePercent(realtimeInfo.getChangePercent());
                dto.setVolume(realtimeInfo.getVolume());
                dto.setAmount(realtimeInfo.getAmount());
                dto.setHighPrice(realtimeInfo.getHighPrice());
                dto.setLowPrice(realtimeInfo.getLowPrice());
                dto.setOpenPrice(realtimeInfo.getOpenPrice());
                dto.setPreClose(realtimeInfo.getPreClose());
                dto.setVolumeDisplay(formatVolume(realtimeInfo.getVolume()));
                dto.setAmountDisplay(formatAmount(realtimeInfo.getAmount()));
            }

            return ResponseEntity.ok(ApiResponse.success(dto));
        }

        // 如果都没有，从旧数据库查找
        StockDTO stock = stockDataService.getStockBySymbol(symbol);
        return ResponseEntity.ok(ApiResponse.success(stock));
    }

    /**
     * 股票自动补全搜索 - 从数据库查询，支持所有A股
     */
    @GetMapping("/public/suggest")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> suggestStocks(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") int limit) {

        // 从数据库搜索
        List<StockBasic> suggestions = stockBasicRepository.searchByKeyword(keyword);
        if (suggestions.size() > limit) {
            suggestions = suggestions.subList(0, limit);
        }

        List<Map<String, String>> result = suggestions.stream()
                .map(stock -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("symbol", stock.getSymbol());
                    map.put("name", stock.getName());
                    map.put("exchange", stock.getExchange());
                    map.put("fullName", stock.getName() + "(" + stock.getSymbol() + ")");
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/public/search")
    public ResponseEntity<ApiResponse<List<StockDTO>>> searchStocks(@RequestParam String keyword) {
        // 从数据库搜索股票
        List<StockBasic> searchResults = stockBasicRepository.searchByKeyword(keyword);
        if (searchResults.size() > 20) {
            searchResults = searchResults.subList(0, 20);
        }

        // 批量获取实时数据
        List<StockDTO> stocks = new ArrayList<>();

        if (!searchResults.isEmpty()) {
            // 构建股票列表用于批量查询
            List<Stock> stockList = searchResults.stream()
                    .map(info -> {
                        Stock stock = new Stock();
                        stock.setSymbol(info.getSymbol());
                        stock.setName(info.getName());
                        stock.setExchange(info.getExchange());
                        return stock;
                    })
                    .collect(Collectors.toList());

            // 批量获取实时数据
            List<StockDTO> realtimeDataList = tencentStockDataService.getRealtimeDataBatch(stockList);

            // 构建symbol到DTO的映射
            Map<String, StockDTO> realtimeDataMap = realtimeDataList.stream()
                    .collect(Collectors.toMap(StockDTO::getSymbol, dto -> dto, (a, b) -> a));

            // 组装结果
            for (StockBasic info : searchResults) {
                StockDTO dto = realtimeDataMap.get(info.getSymbol());

                if (dto == null) {
                    // API没有返回数据，创建基础DTO
                    dto = new StockDTO();
                    dto.setId((long) info.getSymbol().hashCode());
                    dto.setSymbol(info.getSymbol());
                    dto.setName(info.getName());
                    dto.setExchange(info.getExchange());
                    dto.setCurrentPrice(BigDecimal.ZERO);
                    dto.setChangePercent(BigDecimal.ZERO);
                }

                stocks.add(dto);
            }
        }

        return ResponseEntity.ok(ApiResponse.success(stocks));
    }

    private String formatVolume(Long volume) {
        if (volume == null || volume == 0) {
            return "0手";
        }
        double wanShou = volume / 1000000.0;
        if (wanShou >= 10000) {
            return String.format("%.2f亿手", wanShou / 10000);
        } else if (wanShou >= 1) {
            return String.format("%.2f万手", wanShou);
        } else {
            return String.format("%.0f手", volume / 100.0);
        }
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        BigDecimal yi = amount.divide(new BigDecimal("100000000"), 2, RoundingMode.HALF_UP);
        if (yi.compareTo(new BigDecimal("1")) >= 0) {
            return String.format("%.2f亿", yi);
        } else {
            BigDecimal wan = amount.divide(new BigDecimal("10000"), 2, RoundingMode.HALF_UP);
            return String.format("%.2f万", wan);
        }
    }

    @GetMapping("/public/{symbol}/kline")
    public ResponseEntity<ApiResponse<List<KlineDataDTO>>> getKlineData(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1m") String period,
            @RequestParam(defaultValue = "100") int limit) {
        // 优先从缓存获取K线数据
        List<KlineDataDTO> klineData = stockCacheService.getCachedKlineData(symbol, period);

        if (klineData == null || klineData.isEmpty()) {
            // 缓存未命中，尝试从API获取
            log.info("K线缓存未命中，从API获取: {}, 周期: {}", symbol, period);

            // 优先使用腾讯API（更稳定）
            try {
                klineData = tencentStockDataService.getKlineDataFromTencent(symbol, period);
                log.info("腾讯API返回: {}, 周期: {}, 条数: {}", symbol, period, klineData.size());
            } catch (Exception e) {
                log.warn("腾讯K线API异常: {}, 错误: {}", symbol, e.getMessage());
            }

            // 如果腾讯API失败，尝试东方财富API
            if (klineData == null || klineData.isEmpty()) {
                try {
                    log.info("腾讯API返回空，尝试东方财富API: {}, 周期: {}", symbol, period);
                    klineData = eastMoneyStockService.getKlineData(symbol, period);
                    log.info("东方财富API返回: {}, 周期: {}, 条数: {}", symbol, period, klineData.size());
                } catch (Exception e) {
                    log.warn("东方财富K线API异常: {}, 错误: {}", symbol, e.getMessage());
                }
            }

            // 如果都失败，使用本地数据库
            if (klineData == null || klineData.isEmpty()) {
                log.info("API都返回空，使用本地数据库: {}, 周期: {}", symbol, period);
                klineData = stockDataService.getKlineData(symbol, period, limit);
                log.info("本地数据库返回: {}, 周期: {}, 条数: {}", symbol, period, klineData.size());
            }

            // 将获取到的数据缓存（24小时）
            if (klineData != null && !klineData.isEmpty()) {
                stockCacheService.cacheKlineData(symbol, period, klineData);
                log.info("K线数据已缓存: {}, 周期: {}, 条数: {}", symbol, period, klineData.size());
            }
        } else {
            log.info("K线数据从缓存获取: {}, 周期: {}, 条数: {}", symbol, period, klineData.size());
        }

        return ResponseEntity.ok(ApiResponse.success(klineData));
    }

    @GetMapping("/public/{symbol}/minute")
    public ResponseEntity<ApiResponse<List<MinuteDataDTO>>> getMinuteData(
            @PathVariable String symbol) {
        // 优先使用东方财富API
        List<MinuteDataDTO> minuteData = eastMoneyStockService.getMinuteData(symbol);
        if (minuteData.isEmpty()) {
            // 如果失败，尝试腾讯API
            minuteData = tencentStockDataService.getMinuteData(symbol);
        }
        return ResponseEntity.ok(ApiResponse.success(minuteData));
    }

    @GetMapping("/public/{symbol}/detail")
    public ResponseEntity<ApiResponse<StockDetailDTO>> getStockDetail(@PathVariable String symbol) {
        // 从数据库获取股票基本信息
        StockBasic basicInfo = stockBasicRepository.findBySymbol(symbol).orElse(null);

        if (basicInfo == null) {
            // 如果不存在，尝试从数据库获取旧数据
            try {
                StockDetailDTO detail = stockDataService.getStockDetail(symbol);
                return ResponseEntity.ok(ApiResponse.success(detail));
            } catch (Exception e) {
                return ResponseEntity.ok(ApiResponse.error(404, "股票不存在: " + symbol));
            }
        }

        // 从腾讯API获取完整股票信息
        var stockInfo = tencentStockDataService.getStockFullInfo(symbol);

        if (stockInfo == null || stockInfo.getCurrentPrice() == null) {
            return ResponseEntity.ok(ApiResponse.error(500, "获取股票数据失败"));
        }

        // 构建详情DTO
        StockDetailDTO detail = StockDetailDTO.builder()
                .id((long) symbol.hashCode())
                .symbol(symbol)
                .name(basicInfo.getName())
                .exchange(basicInfo.getExchange())
                .industry("")
                .currentPrice(stockInfo.getCurrentPrice())
                .changePrice(stockInfo.getChangePrice())
                .changePercent(stockInfo.getChangePercent())
                .openPrice(stockInfo.getOpenPrice())
                .highPrice(stockInfo.getHighPrice())
                .lowPrice(stockInfo.getLowPrice())
                .preClose(stockInfo.getPreClose())
                .volume(stockInfo.getVolume())
                .amount(stockInfo.getAmount())
                .turnoverRate(stockInfo.getTurnoverRate())
                .amplitude(stockInfo.getAmplitude())
                .peRatio(stockInfo.getPeRatio())
                .pbRatio(stockInfo.getPbRatio())
                .totalShares(stockInfo.getTotalShares())
                .floatShares(stockInfo.getFloatShares())
                .marketCap(stockInfo.getTotalMarketCap())
                .limitUpPrice(stockInfo.getLimitUpPrice())
                .limitDownPrice(stockInfo.getLimitDownPrice())
                .volumeDisplay(formatVolume(stockInfo.getVolume()))
                .amountDisplay(formatAmount(stockInfo.getAmount()))
                .marketCapDisplay(formatMarketCap(stockInfo.getTotalMarketCap()))
                .floatMarketCapDisplay(formatMarketCap(stockInfo.getFloatMarketCap()))
                .build();

        // 获取买卖五档数据
        var realtimeData = tencentStockDataService.getRealtimeData(symbol);
        if (realtimeData != null) {
            detail.setCommissionRatio(calculateCommissionRatio(realtimeData));
            detail.setBidLevels(buildTradeLevels(realtimeData, true));
            detail.setAskLevels(buildTradeLevels(realtimeData, false));
        }

        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    private String formatMarketCap(BigDecimal marketCap) {
        if (marketCap == null || marketCap.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return String.format("%.2f亿", marketCap);
    }

    private BigDecimal calculateCommissionRatio(com.stock.platform.entity.StockRealtimeData data) {
        if (data == null) return BigDecimal.ZERO;

        long bidVolume = (data.getBidVolume1() != null ? data.getBidVolume1() : 0) +
                (data.getBidVolume2() != null ? data.getBidVolume2() : 0) +
                (data.getBidVolume3() != null ? data.getBidVolume3() : 0) +
                (data.getBidVolume4() != null ? data.getBidVolume4() : 0) +
                (data.getBidVolume5() != null ? data.getBidVolume5() : 0);

        long askVolume = (data.getAskVolume1() != null ? data.getAskVolume1() : 0) +
                (data.getAskVolume2() != null ? data.getAskVolume2() : 0) +
                (data.getAskVolume3() != null ? data.getAskVolume3() : 0) +
                (data.getAskVolume4() != null ? data.getAskVolume4() : 0) +
                (data.getAskVolume5() != null ? data.getAskVolume5() : 0);

        long totalVolume = bidVolume + askVolume;
        if (totalVolume == 0) return BigDecimal.ZERO;

        return new BigDecimal(bidVolume - askVolume)
                .multiply(new BigDecimal("100"))
                .divide(new BigDecimal(totalVolume), 2, RoundingMode.HALF_UP);
    }

    private List<TradeLevelDTO> buildTradeLevels(com.stock.platform.entity.StockRealtimeData data, boolean isBid) {
        List<TradeLevelDTO> levels = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            java.math.BigDecimal price = null;
            Long volume = null;

            switch (i) {
                case 1:
                    price = isBid ? data.getBidPrice1() : data.getAskPrice1();
                    volume = isBid ? data.getBidVolume1() : data.getAskVolume1();
                    break;
                case 2:
                    price = isBid ? data.getBidPrice2() : data.getAskPrice2();
                    volume = isBid ? data.getBidVolume2() : data.getAskVolume2();
                    break;
                case 3:
                    price = isBid ? data.getBidPrice3() : data.getAskPrice3();
                    volume = isBid ? data.getBidVolume3() : data.getAskVolume3();
                    break;
                case 4:
                    price = isBid ? data.getBidPrice4() : data.getAskPrice4();
                    volume = isBid ? data.getBidVolume4() : data.getAskVolume4();
                    break;
                case 5:
                    price = isBid ? data.getBidPrice5() : data.getAskPrice5();
                    volume = isBid ? data.getBidVolume5() : data.getAskVolume5();
                    break;
            }

            TradeLevelDTO level = TradeLevelDTO.builder()
                    .level(i)
                    .price(price)
                    .volume(volume)
                    .volumeDisplay(formatHandVolume(volume))
                    .build();
            levels.add(level);
        }
        return levels;
    }

    private String formatHandVolume(Long volume) {
        if (volume == null || volume == 0) {
            return "0手";
        }
        long hand = volume / 100;
        if (hand >= 10000) {
            return String.format("%.2f万手", hand / 10000.0);
        } else {
            return String.format("%d手", hand);
        }
    }

    @GetMapping("/public/realtime")
    public ResponseEntity<ApiResponse<List<StockRealtimeDTO>>> getRealtimeData() {
        List<StockRealtimeDTO> realtimeData = stockDataService.getRealtimeData();
        return ResponseEntity.ok(ApiResponse.success(realtimeData));
    }

    @GetMapping("/public/market-index")
    public ResponseEntity<ApiResponse<List<MarketIndexDTO>>> getMarketIndex() {
        List<MarketIndexDTO> indices = stockDataService.getMarketIndices();
        return ResponseEntity.ok(ApiResponse.success(indices));
    }

    /**
     * 手动触发股票列表同步（需要管理员权限）
     */
    @PostMapping("/admin/sync-stocks")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncStocks() {
        log.info("手动触发股票列表同步");

        long beforeCount = stockBasicRepository.count();

        // 异步执行同步，避免阻塞请求
        CompletableFuture.runAsync(() -> {
            stockSyncService.manualFullSync();
        });

        Map<String, Object> result = new HashMap<>();
        result.put("beforeCount", beforeCount);
        result.put("message", "同步任务已启动，请稍后查询股票数量");

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取股票统计信息
     */
    @GetMapping("/public/stock-stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStockStats() {
        long count = stockBasicRepository.count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", count);
        stats.put("lastUpdateTime", new java.util.Date());

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * 获取市场状态（开市/闭市）
     */
    @GetMapping("/public/market-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMarketStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("isTradingTime", MarketTimeUtil.isTradingTime());
        status.put("isTradingDay", MarketTimeUtil.isTradingDay());
        status.put("status", MarketTimeUtil.getMarketStatus());
        status.put("statusCode", MarketTimeUtil.getMarketStatusCode());
        status.put("nextOpenTime", MarketTimeUtil.getNextOpenTime());
        status.put("currentTime", java.time.LocalDateTime.now());

        return ResponseEntity.ok(ApiResponse.success(status));
    }

    // ==================== 新版股票同步接口 ====================

    /**
     * 获取股票同步状态
     */
    @GetMapping("/public/sync-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSyncStatus() {
        Map<String, Object> status = stockSyncService.getSyncStatus();
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    /**
     * 手动触发全量同步（需要管理员权限）
     * 用于首次同步或修复数据
     */
    @PostMapping("/admin/full-sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> fullSync() {
        log.info("手动触发全量股票同步");

        Map<String, Object> beforeStatus = stockSyncService.getSyncStatus();

        // 异步执行同步，避免阻塞请求
        CompletableFuture.runAsync(() -> {
            stockSyncService.manualFullSync();
        });

        Map<String, Object> result = new HashMap<>();
        result.put("beforeCount", beforeStatus.get("stockBasicCount"));
        result.put("message", "全量同步任务已启动，将分批获取所有A股(5000+只)，请稍后查询同步状态");
        result.put("estimatedTime", "约需5-10分钟");

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 手动触发增量同步（需要管理员权限）
     * 用于检查并添加新上市的股票
     */
    @PostMapping("/admin/incremental-sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> incrementalSync() {
        log.info("手动触发增量股票同步");

        Map<String, Object> beforeStatus = stockSyncService.getSyncStatus();

        // 异步执行同步，避免阻塞请求
        CompletableFuture.runAsync(() -> {
            stockSyncService.incrementalSync();
        });

        Map<String, Object> result = new HashMap<>();
        result.put("beforeCount", beforeStatus.get("stockBasicCount"));
        result.put("message", "增量同步任务已启动，将检查并添加新股票");

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 同步StockBasic到Stock表（需要管理员权限）
     */
    @PostMapping("/admin/sync-to-stock-table")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncToStockTable() {
        log.info("手动触发Stock表同步");

        // 异步执行同步
        CompletableFuture.runAsync(() -> {
            stockSyncService.syncToStockTable();
        });

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Stock表同步任务已启动");

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
