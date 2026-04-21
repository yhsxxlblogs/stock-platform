package com.stock.platform.service;

import com.stock.platform.dto.*;
import com.stock.platform.entity.Stock;
import com.stock.platform.entity.StockDailyData;
import com.stock.platform.entity.StockRealtimeData;
import com.stock.platform.repository.StockDailyDataRepository;
import com.stock.platform.repository.StockRealtimeDataRepository;
import com.stock.platform.repository.StockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StockDataService {

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockDailyDataRepository dailyDataRepository;

    @Autowired
    private StockRealtimeDataRepository realtimeDataRepository;

    @Autowired
    private EastMoneyStockService eastMoneyStockService;

    @Autowired
    private TencentStockDataService tencentStockDataService;

    private final RestTemplate restTemplate = new RestTemplate();

    // 新浪股票API
    private static final String SINA_REALTIME_API = "https://hq.sinajs.cn/list=";

    // 热门股票代码列表（用于行情中心展示，避免API请求过多）
    private static final List<String> HOT_STOCKS = Arrays.asList(
        "600519", "600036", "601318", "600276", "600887",  // 上海主板
        "601398", "601288", "601939", "601988", "600900",
        "600030", "601668", "601857", "601088", "601628",
        "601899", "600309", "600809", "603288", "600570",
        "000001", "000002", "000333", "000858", "002415",  // 深圳主板/中小板
        "002594", "002230", "002236", "002460", "002466",
        "300750", "300059", "300015", "300122", "300274",  // 创业板
        "300014", "300413",
        "688981", "688008", "688012", "688111", "688169"   // 科创板
    );

    @Transactional(readOnly = true)
    public List<StockDTO> getAllStocks() {
        // 只获取热门股票，避免API请求过多
        List<Stock> hotStocks = stockRepository.findByStatus(1).stream()
                .filter(stock -> HOT_STOCKS.contains(stock.getSymbol()))
                .limit(50) // 最多展示50只
                .collect(Collectors.toList());
        
        if (hotStocks.isEmpty()) {
            // 如果没有热门股票，返回前50只
            hotStocks = stockRepository.findByStatus(1).stream()
                    .limit(50)
                    .collect(Collectors.toList());
        }
        
        if (hotStocks.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 从腾讯API批量获取实时数据
        List<StockDTO> result = tencentStockDataService.getRealtimeDataBatch(hotStocks);
        
        // 如果API获取失败，回退到本地数据库
        if (result.isEmpty()) {
            log.warn("从APIGet实时数据Failed，使用本地数据库数据");
            return hotStocks.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
        }
        
        return result;
    }

    /**
     * 获取大盘指数（优先从腾讯API获取，失败则使用东方财富API）
     */
    @Transactional(readOnly = true)
    public List<MarketIndexDTO> getMarketIndices() {
        List<MarketIndexDTO> indices = new ArrayList<>();

        // 优先从腾讯API获取大盘指数，失败则使用东方财富API
        // 上证指数
        var shIndex = tencentStockDataService.getMarketIndex("1.000001");
        if (shIndex == null || shIndex.getCurrentPrice() == null) {
            shIndex = eastMoneyStockService.getMarketIndex("1.000001");
        }
        if (shIndex != null && shIndex.getCurrentPrice() != null) {
            indices.add(MarketIndexDTO.builder()
                    .name("上证指数")
                    .currentPrice(shIndex.getCurrentPrice())
                    .changePrice(shIndex.getChangePrice())
                    .changePercent(shIndex.getChangePercent())
                    .preClose(shIndex.getPreClose())
                    .build());
        } else {
            indices.add(getDefaultIndex("上证指数"));
        }

        // 深证成指
        var szIndex = tencentStockDataService.getMarketIndex("0.399001");
        if (szIndex == null || szIndex.getCurrentPrice() == null) {
            szIndex = eastMoneyStockService.getMarketIndex("0.399001");
        }
        if (szIndex != null && szIndex.getCurrentPrice() != null) {
            indices.add(MarketIndexDTO.builder()
                    .name("深证成指")
                    .currentPrice(szIndex.getCurrentPrice())
                    .changePrice(szIndex.getChangePrice())
                    .changePercent(szIndex.getChangePercent())
                    .preClose(szIndex.getPreClose())
                    .build());
        } else {
            indices.add(getDefaultIndex("深证成指"));
        }

        // 创业板指
        var cyIndex = tencentStockDataService.getMarketIndex("0.399006");
        if (cyIndex == null || cyIndex.getCurrentPrice() == null) {
            cyIndex = eastMoneyStockService.getMarketIndex("0.399006");
        }
        if (cyIndex != null && cyIndex.getCurrentPrice() != null) {
            indices.add(MarketIndexDTO.builder()
                    .name("创业板指")
                    .currentPrice(cyIndex.getCurrentPrice())
                    .changePrice(cyIndex.getChangePrice())
                    .changePercent(cyIndex.getChangePercent())
                    .preClose(cyIndex.getPreClose())
                    .build());
        } else {
            indices.add(getDefaultIndex("创业板指"));
        }

        // 科创50
        var kcIndex = tencentStockDataService.getMarketIndex("1.000688");
        if (kcIndex == null || kcIndex.getCurrentPrice() == null) {
            kcIndex = eastMoneyStockService.getMarketIndex("1.000688");
        }
        if (kcIndex != null && kcIndex.getCurrentPrice() != null) {
            indices.add(MarketIndexDTO.builder()
                    .name("科创50")
                    .currentPrice(kcIndex.getCurrentPrice())
                    .changePrice(kcIndex.getChangePrice())
                    .changePercent(kcIndex.getChangePercent())
                    .preClose(kcIndex.getPreClose())
                    .build());
        } else {
            indices.add(getDefaultIndex("科创50"));
        }

        return indices;
    }

    private MarketIndexDTO getDefaultIndex(String name) {
        BigDecimal defaultPrice = switch (name) {
            case "上证指数" -> new BigDecimal("3050.00");
            case "深证成指" -> new BigDecimal("9850.00");
            case "创业板指" -> new BigDecimal("1980.00");
            case "科创50" -> new BigDecimal("890.00");
            default -> new BigDecimal("1000.00");
        };

        return MarketIndexDTO.builder()
                .name(name)
                .currentPrice(defaultPrice)
                .changePrice(BigDecimal.ZERO)
                .changePercent(BigDecimal.ZERO)
                .preClose(defaultPrice)
                .build();
    }

    @Transactional(readOnly = true)
    public StockDTO getStockBySymbol(String symbol) {
        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new RuntimeException("股票不存在: " + symbol));
        return convertToDTO(stock);
    }

    @Transactional(readOnly = true)
    public List<StockDTO> searchStocks(String keyword) {
        return stockRepository.searchStocks(keyword).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<KlineDataDTO> getKlineData(String symbol, String period, int limit) {
        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new RuntimeException("股票不存在: " + symbol));

        LocalDate endDate = LocalDate.now();
        LocalDate startDate;

        switch (period.toLowerCase()) {
            case "1m":
                startDate = endDate.minusMonths(1);
                break;
            case "3m":
                startDate = endDate.minusMonths(3);
                break;
            case "6m":
                startDate = endDate.minusMonths(6);
                break;
            case "1y":
                startDate = endDate.minusYears(1);
                break;
            default:
                startDate = endDate.minusMonths(3);
        }

        List<StockDailyData> dailyDataList = dailyDataRepository
                .findByStockIdAndTradeDateBetweenOrderByTradeDateAsc(stock.getId(), startDate, endDate);

        return dailyDataList.stream()
                .map(this::convertToKlineDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StockRealtimeDTO> getRealtimeData() {
        return realtimeDataRepository.findAllActiveStocks().stream()
                .map(this::convertToRealtimeDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateRealtimeData() {
        try {
            List<Stock> stocks = stockRepository.findByStatus(1);
            
            for (Stock stock : stocks) {
                String sinaSymbol = convertToSinaSymbol(stock.getSymbol());
                String url = SINA_REALTIME_API + sinaSymbol;
                
                try {
                    String response = restTemplate.getForObject(url, String.class);
                    if (response != null && !response.isEmpty()) {
                        parseAndSaveRealtimeData(stock, response);
                    }
                } catch (Exception e) {
                    log.warn("新浪API调用Failed，使用本地数据: {}", stock.getSymbol());
                    generateLocalRealtimeData(stock);
                }
            }
        } catch (Exception e) {
            log.error("Update实时数据Failed: {}", e.getMessage());
        }
    }

    /**
     * 当外部API不可用时，生成本地模拟数据
     */
    private void generateLocalRealtimeData(Stock stock) {
        try {
            StockRealtimeData realtimeData = realtimeDataRepository.findByStockId(stock.getId())
                    .orElse(new StockRealtimeData());

            realtimeData.setStock(stock);
            
            // 基于已有数据生成模拟波动
            BigDecimal basePrice = realtimeData.getPreClose() != null ? 
                    realtimeData.getPreClose() : new BigDecimal("10.00");
            
            // 生成随机波动 (-2% 到 +2%)
            double randomChange = (Math.random() - 0.5) * 0.04;
            BigDecimal currentPrice = basePrice.multiply(BigDecimal.valueOf(1 + randomChange))
                    .setScale(2, RoundingMode.HALF_UP);
            
            BigDecimal changePrice = currentPrice.subtract(basePrice);
            BigDecimal changePercent = changePrice.divide(basePrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            
            realtimeData.setCurrentPrice(currentPrice);
            realtimeData.setChangePrice(changePrice);
            realtimeData.setChangePercent(changePercent);
            realtimeData.setOpenPrice(basePrice.multiply(BigDecimal.valueOf(0.99 + Math.random() * 0.02)).setScale(2, RoundingMode.HALF_UP));
            realtimeData.setHighPrice(currentPrice.multiply(BigDecimal.valueOf(1.01 + Math.random() * 0.02)).setScale(2, RoundingMode.HALF_UP));
            realtimeData.setLowPrice(currentPrice.multiply(BigDecimal.valueOf(0.97 + Math.random() * 0.02)).setScale(2, RoundingMode.HALF_UP));
            realtimeData.setPreClose(basePrice);
            realtimeData.setVolume((long) (Math.random() * 10000000));
            realtimeData.setAmount(realtimeData.getCurrentPrice().multiply(BigDecimal.valueOf(realtimeData.getVolume())));
            
            // 买卖五档数据
            for (int i = 1; i <= 5; i++) {
                BigDecimal bidPrice = currentPrice.multiply(BigDecimal.valueOf(1 - i * 0.001)).setScale(2, RoundingMode.HALF_UP);
                BigDecimal askPrice = currentPrice.multiply(BigDecimal.valueOf(1 + i * 0.001)).setScale(2, RoundingMode.HALF_UP);
                long volume = (long) (Math.random() * 10000);
                
                switch (i) {
                    case 1:
                        realtimeData.setBidPrice1(bidPrice); realtimeData.setBidVolume1(volume);
                        realtimeData.setAskPrice1(askPrice); realtimeData.setAskVolume1(volume);
                        break;
                    case 2:
                        realtimeData.setBidPrice2(bidPrice); realtimeData.setBidVolume2(volume);
                        realtimeData.setAskPrice2(askPrice); realtimeData.setAskVolume2(volume);
                        break;
                    case 3:
                        realtimeData.setBidPrice3(bidPrice); realtimeData.setBidVolume3(volume);
                        realtimeData.setAskPrice3(askPrice); realtimeData.setAskVolume3(volume);
                        break;
                    case 4:
                        realtimeData.setBidPrice4(bidPrice); realtimeData.setBidVolume4(volume);
                        realtimeData.setAskPrice4(askPrice); realtimeData.setAskVolume4(volume);
                        break;
                    case 5:
                        realtimeData.setBidPrice5(bidPrice); realtimeData.setBidVolume5(volume);
                        realtimeData.setAskPrice5(askPrice); realtimeData.setAskVolume5(volume);
                        break;
                }
            }
            
            realtimeDataRepository.save(realtimeData);
        } catch (Exception ex) {
            log.error("生成本地实时数据Failed {}: {}", stock.getSymbol(), ex.getMessage());
        }
    }

    private void parseAndSaveRealtimeData(Stock stock, String response) {
        try {
            // 解析新浪返回的数据格式
            String[] parts = response.split("=");
            if (parts.length < 2) return;
            
            String dataStr = parts[1].replace("\"", "").replace(";", "");
            String[] dataArr = dataStr.split(",");
            
            if (dataArr.length < 30) return;

            StockRealtimeData realtimeData = realtimeDataRepository.findByStockId(stock.getId())
                    .orElse(new StockRealtimeData());

            realtimeData.setStock(stock);
            realtimeData.setCurrentPrice(new BigDecimal(dataArr[3]));
            realtimeData.setHighPrice(new BigDecimal(dataArr[4]));
            realtimeData.setLowPrice(new BigDecimal(dataArr[5]));
            realtimeData.setOpenPrice(new BigDecimal(dataArr[1]));
            realtimeData.setPreClose(new BigDecimal(dataArr[2]));
            realtimeData.setVolume(Long.parseLong(dataArr[8]));
            realtimeData.setAmount(new BigDecimal(dataArr[9]));
            realtimeData.setBidPrice1(new BigDecimal(dataArr[11]));
            realtimeData.setBidVolume1(Long.parseLong(dataArr[10]));
            realtimeData.setAskPrice1(new BigDecimal(dataArr[21]));
            realtimeData.setAskVolume1(Long.parseLong(dataArr[20]));

            // 计算涨跌幅
            BigDecimal changePrice = realtimeData.getCurrentPrice().subtract(realtimeData.getPreClose());
            BigDecimal changePercent = changePrice.divide(realtimeData.getPreClose(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            realtimeData.setChangePrice(changePrice);
            realtimeData.setChangePercent(changePercent);

            realtimeDataRepository.save(realtimeData);
        } catch (Exception e) {
            log.error("解析股票 {} 数据Failed: {}", stock.getSymbol(), e.getMessage());
        }
    }

    private String convertToSinaSymbol(String symbol) {
        // 将股票代码转换为新浪格式
        if (symbol.startsWith("6")) {
            return "sh" + symbol;
        } else {
            return "sz" + symbol;
        }
    }

    private StockDTO convertToDTO(Stock stock) {
        StockDTO dto = StockDTO.builder()
                .id(stock.getId())
                .symbol(stock.getSymbol())
                .name(stock.getName())
                .exchange(stock.getExchange())
                .industry(stock.getIndustry())
                .marketCap(stock.getMarketCap())
                .status(stock.getStatus())
                .createdAt(stock.getCreatedAt())
                .build();

        // 尝试获取实时数据
        realtimeDataRepository.findByStockId(stock.getId()).ifPresent(realtime -> {
            dto.setCurrentPrice(realtime.getCurrentPrice());
            dto.setChangePrice(realtime.getChangePrice());
            dto.setChangePercent(realtime.getChangePercent());
            dto.setVolume(realtime.getVolume());
            dto.setAmount(realtime.getAmount());
            dto.setHighPrice(realtime.getHighPrice());
            dto.setLowPrice(realtime.getLowPrice());
            dto.setOpenPrice(realtime.getOpenPrice());
            dto.setPreClose(realtime.getPreClose());
            // 添加格式化显示字段
            dto.setVolumeDisplay(formatVolume(realtime.getVolume()));
            dto.setAmountDisplay(formatAmount(realtime.getAmount()));
        });

        return dto;
    }

    private KlineDataDTO convertToKlineDTO(StockDailyData data) {
        return KlineDataDTO.builder()
                .date(data.getTradeDate())
                .open(data.getOpenPrice())
                .high(data.getHighPrice())
                .low(data.getLowPrice())
                .close(data.getClosePrice())
                .volume(data.getVolume())
                .amount(data.getAmount())
                .changePercent(data.getChangePercent())
                .build();
    }

    private StockRealtimeDTO convertToRealtimeDTO(StockRealtimeData data) {
        return StockRealtimeDTO.builder()
                .stockId(data.getStock().getId())
                .symbol(data.getStock().getSymbol())
                .name(data.getStock().getName())
                .currentPrice(data.getCurrentPrice())
                .changePrice(data.getChangePrice())
                .changePercent(data.getChangePercent())
                .volume(data.getVolume())
                .amount(data.getAmount())
                .bidPrice1(data.getBidPrice1())
                .bidVolume1(data.getBidVolume1())
                .askPrice1(data.getAskPrice1())
                .askVolume1(data.getAskVolume1())
                .highPrice(data.getHighPrice())
                .lowPrice(data.getLowPrice())
                .openPrice(data.getOpenPrice())
                .preClose(data.getPreClose())
                .updateTime(data.getUpdateTime())
                .build();
    }

    @Transactional(readOnly = true)
    public StockDetailDTO getStockDetail(String symbol) {
        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new RuntimeException("股票不存在: " + symbol));

        StockDetailDTO.StockDetailDTOBuilder builder = StockDetailDTO.builder()
                .id(stock.getId())
                .symbol(stock.getSymbol())
                .name(stock.getName())
                .exchange(stock.getExchange())
                .industry(stock.getIndustry())
                .marketCap(stock.getMarketCap());

        // 优先从腾讯API获取完整股票信息（包含股本、财务指标等）
        com.stock.platform.dto.StockInfoDTO stockInfo = tencentStockDataService.getStockFullInfo(symbol);
        
        if (stockInfo != null && stockInfo.getCurrentPrice() != null) {
            // 使用腾讯API完整数据
            BigDecimal currentPrice = stockInfo.getCurrentPrice();
            BigDecimal preClose = stockInfo.getPreClose();
            BigDecimal highPrice = stockInfo.getHighPrice();
            BigDecimal lowPrice = stockInfo.getLowPrice();
            Long volume = stockInfo.getVolume();
            BigDecimal amount = stockInfo.getAmount();
            Long floatShares = stockInfo.getFloatShares();
            Long totalShares = stockInfo.getTotalShares();
            
            // 使用腾讯API返回的市值数据（更精确）
            BigDecimal totalMarketCap = stockInfo.getTotalMarketCap();
            BigDecimal floatMarketCap = stockInfo.getFloatMarketCap();
            
            builder.currentPrice(currentPrice)
                    .changePrice(stockInfo.getChangePrice())
                    .changePercent(stockInfo.getChangePercent())
                    .openPrice(stockInfo.getOpenPrice())
                    .highPrice(highPrice)
                    .lowPrice(lowPrice)
                    .preClose(preClose)
                    .volume(volume)
                    .amount(amount)
                    .turnoverRate(stockInfo.getTurnoverRate())  // 使用API返回的换手率
                    .amplitude(stockInfo.getAmplitude())  // 使用API返回的振幅
                    .limitUpPrice(stockInfo.getLimitUpPrice())  // 使用API返回的涨停价
                    .limitDownPrice(stockInfo.getLimitDownPrice())  // 使用API返回的跌停价
                    .totalShares(totalShares)
                    .floatShares(floatShares)
                    .marketCap(totalMarketCap)
                    .peRatio(stockInfo.getPeRatio())  // 市盈率
                    .pbRatio(stockInfo.getPbRatio())  // 市净率
                    // 格式化展示字段
                    .volumeDisplay(formatVolume(volume))
                    .amountDisplay(formatAmount(amount))
                    .marketCapDisplay(formatMarketCap(totalMarketCap))
                    .floatMarketCapDisplay(formatMarketCap(floatMarketCap));
            
            // 获取实时数据用于计算委比和买卖五档
            StockRealtimeData realtimeData = tencentStockDataService.getRealtimeData(symbol);
            if (realtimeData != null) {
                builder.commissionRatio(calculateCommissionRatio(realtimeData))
                        .bidLevels(buildTradeLevels(realtimeData, true))
                        .askLevels(buildTradeLevels(realtimeData, false));
            }
        } else {
            // 如果腾讯API失败，使用本地数据库数据
            realtimeDataRepository.findByStockId(stock.getId()).ifPresent(realtime -> {
                BigDecimal preClose = realtime.getPreClose();
                BigDecimal highPrice = realtime.getHighPrice();
                BigDecimal lowPrice = realtime.getLowPrice();
                Long volume = realtime.getVolume();
                BigDecimal amount = realtime.getAmount();
                Long floatShares = stock.getFloatShares();
                Long totalShares = stock.getTotalShares();
                BigDecimal currentPrice = realtime.getCurrentPrice();
                BigDecimal marketCap = calculateMarketCap(currentPrice, totalShares);
                BigDecimal floatMarketCap = calculateMarketCap(currentPrice, floatShares);
                
                builder.currentPrice(currentPrice)
                        .changePrice(realtime.getChangePrice())
                        .changePercent(realtime.getChangePercent())
                        .openPrice(realtime.getOpenPrice())
                        .highPrice(highPrice)
                        .lowPrice(lowPrice)
                        .preClose(preClose)
                        .volume(volume)
                        .amount(amount)
                        .turnoverRate(calculateTurnoverRate(volume, floatShares))
                        .amplitude(calculateAmplitude(highPrice, lowPrice, preClose))
                        .limitUpPrice(calculateLimitUpPrice(preClose))
                        .limitDownPrice(calculateLimitDownPrice(preClose))
                        .totalShares(totalShares)
                        .floatShares(floatShares)
                        .marketCap(marketCap)
                        // 格式化展示字段
                        .volumeDisplay(formatVolume(volume))
                        .amountDisplay(formatAmount(amount))
                        .marketCapDisplay(formatMarketCap(marketCap))
                        .floatMarketCapDisplay(formatMarketCap(floatMarketCap))
                        .commissionRatio(calculateCommissionRatio(realtime))
                        .bidLevels(buildTradeLevels(realtime, true))
                        .askLevels(buildTradeLevels(realtime, false));
            });
        }

        return builder.build();
    }

    /**
     * 计算换手率（%）
     * 换手率 = 成交量 / 流通股本 * 100%
     * 如果流通股本为空，使用默认值估算（假设流通股本为100亿股）
     */
    private BigDecimal calculateTurnoverRate(Long volume, Long floatShares) {
        if (volume == null) {
            return BigDecimal.ZERO;
        }
        // 如果流通股本为空，使用默认值100亿股
        long shares = (floatShares == null || floatShares == 0) ? 10000000000L : floatShares;
        return new BigDecimal(volume).multiply(new BigDecimal("100"))
                .divide(new BigDecimal(shares), 2, RoundingMode.HALF_UP);
    }

    /**
     * 计算振幅（%）
     * 振幅 = (最高价 - 最低价) / 昨收 * 100%
     */
    private BigDecimal calculateAmplitude(BigDecimal highPrice, BigDecimal lowPrice, BigDecimal preClose) {
        if (highPrice == null || lowPrice == null || preClose == null || 
            preClose.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal diff = highPrice.subtract(lowPrice);
        return diff.multiply(new BigDecimal("100"))
                .divide(preClose, 2, RoundingMode.HALF_UP);
    }

    /**
     * 计算涨停价（A股主板涨停为+10%，科创板/创业板为+20%）
     * 简化处理，统一按10%计算
     */
    private BigDecimal calculateLimitUpPrice(BigDecimal preClose) {
        if (preClose == null || preClose.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        // 涨停价 = 昨收 * 1.10，保留两位小数
        return preClose.multiply(new BigDecimal("1.10"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算跌停价（A股主板跌停为-10%，科创板/创业板为-20%）
     * 简化处理，统一按10%计算
     */
    private BigDecimal calculateLimitDownPrice(BigDecimal preClose) {
        if (preClose == null || preClose.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        // 跌停价 = 昨收 * 0.90，保留两位小数
        return preClose.multiply(new BigDecimal("0.90"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算市值（亿元）
     * 市值 = 当前价 * 股本 / 100000000
     */
    private BigDecimal calculateMarketCap(BigDecimal currentPrice, Long shares) {
        if (currentPrice == null || shares == null || shares == 0) {
            return BigDecimal.ZERO;
        }
        return currentPrice.multiply(new BigDecimal(shares))
                .divide(new BigDecimal("100000000"), 2, RoundingMode.HALF_UP);
    }

    /**
     * 格式化成交量显示（转换为万手）
     * 1手 = 100股，所以万手 = 股数 / 100 / 10000 = 股数 / 1000000
     */
    private String formatVolume(Long volume) {
        if (volume == null || volume == 0) {
            return "0手";
        }
        // 转换为万手
        double wanShou = volume / 1000000.0;
        if (wanShou >= 10000) {
            // 超过1亿手，显示为亿手
            return String.format("%.2f亿手", wanShou / 10000);
        } else if (wanShou >= 1) {
            // 显示为万手
            return String.format("%.2f万手", wanShou);
        } else {
            // 显示为手
            return String.format("%.0f手", volume / 100.0);
        }
    }

    /**
     * 格式化成交额显示
     * 超过1亿显示为"xx亿"，否则显示为"xx万"
     */
    private String formatAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        // 转换为亿元
        BigDecimal yi = amount.divide(new BigDecimal("100000000"), 2, RoundingMode.HALF_UP);
        if (yi.compareTo(new BigDecimal("1")) >= 0) {
            return String.format("%.2f亿", yi);
        } else {
            // 转换为万元
            BigDecimal wan = amount.divide(new BigDecimal("10000"), 2, RoundingMode.HALF_UP);
            return String.format("%.2f万", wan);
        }
    }

    /**
     * 格式化市值显示（亿元）
     * 注意：传入的marketCap应该是以亿元为单位的数值
     */
    private String formatMarketCap(BigDecimal marketCap) {
        if (marketCap == null || marketCap.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return String.format("%.2f亿", marketCap);
    }

    /**
     * 计算委比（%）
     * 委比 = (委买手数 - 委卖手数) / (委买手数 + 委卖手数) * 100%
     */
    private BigDecimal calculateCommissionRatio(StockRealtimeData data) {
        if (data == null) {
            return BigDecimal.ZERO;
        }

        // 计算委买总量（股数转手数）
        long bidVolume = (data.getBidVolume1() != null ? data.getBidVolume1() : 0) +
                        (data.getBidVolume2() != null ? data.getBidVolume2() : 0) +
                        (data.getBidVolume3() != null ? data.getBidVolume3() : 0) +
                        (data.getBidVolume4() != null ? data.getBidVolume4() : 0) +
                        (data.getBidVolume5() != null ? data.getBidVolume5() : 0);

        // 计算委卖总量（股数转手数）
        long askVolume = (data.getAskVolume1() != null ? data.getAskVolume1() : 0) +
                        (data.getAskVolume2() != null ? data.getAskVolume2() : 0) +
                        (data.getAskVolume3() != null ? data.getAskVolume3() : 0) +
                        (data.getAskVolume4() != null ? data.getAskVolume4() : 0) +
                        (data.getAskVolume5() != null ? data.getAskVolume5() : 0);

        long totalVolume = bidVolume + askVolume;
        if (totalVolume == 0) {
            return BigDecimal.ZERO;
        }

        // 委比 = (委买 - 委卖) / (委买 + 委卖) * 100%
        BigDecimal diff = new BigDecimal(bidVolume - askVolume);
        return diff.multiply(new BigDecimal("100"))
                .divide(new BigDecimal(totalVolume), 2, RoundingMode.HALF_UP);
    }

    private List<TradeLevelDTO> buildTradeLevels(StockRealtimeData data, boolean isBid) {
        List<TradeLevelDTO> levels = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            BigDecimal price = null;
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
                    .volumeDisplay(formatHandVolume(volume))  // 添加手单位显示
                    .build();
            levels.add(level);
        }
        return levels;
    }
    
    /**
     * 格式化盘口成交量显示（手）
     * 1手 = 100股
     */
    private String formatHandVolume(Long volume) {
        if (volume == null || volume == 0) {
            return "0手";
        }
        // 转换为手
        long hand = volume / 100;
        if (hand >= 10000) {
            // 超过1万手，显示为万手
            return String.format("%.2f万手", hand / 10000.0);
        } else {
            return String.format("%d手", hand);
        }
    }
}
