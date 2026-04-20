package com.stock.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.platform.annotation.Nullable;
import com.stock.platform.dto.*;
import com.stock.platform.entity.Stock;
import com.stock.platform.entity.StockDailyData;
import com.stock.platform.entity.StockRealtimeData;
import com.stock.platform.repository.StockDailyDataRepository;
import com.stock.platform.repository.StockRealtimeDataRepository;
import com.stock.platform.repository.StockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TencentStockDataService {

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockDailyDataRepository dailyDataRepository;

    @Autowired
    private StockRealtimeDataRepository realtimeDataRepository;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 腾讯股票API
    private static final String TENCENT_REALTIME_API = "https://qt.gtimg.cn/q=";
    private static final String TENCENT_KLINE_API = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get";
    private static final String TENCENT_MINUTE_API = "https://web.ifzq.gtimg.cn/appstock/app/minute/query";

    // 缓存最近一次的实时数据，用于API失败时降级
    private final Map<String, StockDTO> realtimeDataCache = new ConcurrentHashMap<>();
    private volatile long lastSuccessfulFetchTime = 0;
    private static final long CACHE_VALIDITY_MS = 60000; // 缓存有效期60秒

    /**
     * 批量获取股票实时数据（用于前端展示，不保存到数据库）
     * 支持优雅降级：API失败时返回缓存数据
     */
    public List<StockDTO> getRealtimeDataBatch(List<Stock> stocks) {
        List<StockDTO> result = new ArrayList<>();
        boolean apiSuccess = false;

        try {
            // 分批处理，每批30只（减少每批数量，降低API压力）
            int batchSize = 30;
            for (int i = 0; i < stocks.size(); i += batchSize) {
                List<Stock> batch = stocks.subList(i, Math.min(i + batchSize, stocks.size()));
                List<StockDTO> batchResult = fetchBatchRealtimeDataWithRetry(batch, 2);
                if (!batchResult.isEmpty()) {
                    result.addAll(batchResult);
                    apiSuccess = true;
                }
                Thread.sleep(100); // 增加延迟，避免请求过快
            }

            // 如果API调用成功，更新缓存
            if (apiSuccess) {
                lastSuccessfulFetchTime = System.currentTimeMillis();
                for (StockDTO dto : result) {
                    realtimeDataCache.put(dto.getSymbol(), dto);
                }
                log.info("实时数据获取成功，共 {} 条，已更新缓存", result.size());
            }

        } catch (Exception e) {
            log.error("批量获取实时数据异常: {}", e.getMessage());
        }

        // 如果API调用失败或返回数据为空，使用缓存数据
        if (result.isEmpty() && !realtimeDataCache.isEmpty()) {
            log.warn("API获取失败，使用缓存数据，缓存数量: {}", realtimeDataCache.size());
            for (Stock stock : stocks) {
                StockDTO cached = realtimeDataCache.get(stock.getSymbol());
                if (cached != null) {
                    // 标记为缓存数据
                    cached.setFromCache(true);
                    result.add(cached);
                }
            }
        }

        return result;
    }

    /**
     * 带重试机制的批量获取
     */
    private List<StockDTO> fetchBatchRealtimeDataWithRetry(List<Stock> stocks, int maxRetry) {
        List<StockDTO> result = new ArrayList<>();
        int attempt = 0;

        while (attempt < maxRetry && result.isEmpty()) {
            try {
                result = fetchBatchRealtimeData(stocks);
                if (!result.isEmpty()) {
                    break;
                }
            } catch (Exception e) {
                log.warn("第 {} 次尝试获取数据失败: {}", attempt + 1, e.getMessage());
            }
            attempt++;
            if (attempt < maxRetry) {
                try {
                    Thread.sleep(500); // 重试前等待500ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return result;
    }
    
    private List<StockDTO> fetchBatchRealtimeData(List<Stock> stocks) {
        List<StockDTO> result = new ArrayList<>();
        try {
            String symbols = stocks.stream()
                    .map(s -> convertToTencentSymbol(s.getSymbol()))
                    .collect(Collectors.joining(","));

            String url = TENCENT_REALTIME_API + symbols;
            log.info("调用腾讯APIGet股票数据，URL: {}", url);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Referer", "https://qt.gtimg.cn");
            headers.set("Accept", "*/*");
            headers.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            if (response.getBody() != null) {
                log.info("腾讯API返回数据长度: {}", response.getBody().length());
                result = parseTencentRealtimeDataToDTO(response.getBody(), stocks);
                log.info("解析到 {} stocks数据", result.size());
            } else {
                log.warn("腾讯API返回空数据");
            }
        } catch (Exception e) {
            log.warn("腾讯API批量Get数据Failed: {}", e.getMessage());
        }
        return result;
    }
    
    private List<StockDTO> parseTencentRealtimeDataToDTO(String data, List<Stock> stocks) {
        List<StockDTO> result = new ArrayList<>();
        try {
            String[] lines = data.split(";");
            Map<String, Stock> stockMap = stocks.stream()
                    .collect(Collectors.toMap(Stock::getSymbol, s -> s));

            for (String line : lines) {
                if (line.trim().isEmpty()) continue;

                int start = line.indexOf('"');
                int end = line.lastIndexOf('"');
                if (start == -1 || end == -1 || start >= end) continue;

                String content = line.substring(start + 1, end);
                String[] fields = content.split("~");
                if (fields.length < 45) continue;

                String symbol = fields[2];
                Stock stock = stockMap.get(symbol);
                if (stock == null) continue;

                // 构建StockDTO
                BigDecimal currentPrice = new BigDecimal(fields[3]);
                BigDecimal preClose = new BigDecimal(fields[4]);

                // 使用腾讯返回的涨跌额和涨跌幅
                // fields[31] = 涨跌, fields[32] = 涨跌幅%
                BigDecimal changePrice;
                BigDecimal changePercent;

                if (fields.length > 31 && !fields[31].isEmpty()) {
                    changePrice = new BigDecimal(fields[31]);
                } else {
                    changePrice = currentPrice.subtract(preClose);
                }

                if (fields.length > 32 && !fields[32].isEmpty()) {
                    changePercent = new BigDecimal(fields[32]);
                } else {
                    changePercent = changePrice.multiply(BigDecimal.valueOf(100))
                            .divide(preClose, 2, RoundingMode.HALF_UP);
                }

                Long volume = Long.parseLong(fields[36]) * 100; // 手转股
                BigDecimal amount = new BigDecimal(fields[37]).multiply(new BigDecimal("10000"));

                StockDTO dto = StockDTO.builder()
                        .id(stock.getId())
                        .symbol(stock.getSymbol())
                        .name(stock.getName())
                        .exchange(stock.getExchange())
                        .industry(stock.getIndustry())
                        .marketCap(stock.getMarketCap())
                        .status(stock.getStatus())
                        .createdAt(stock.getCreatedAt())
                        .currentPrice(currentPrice)
                        .changePrice(changePrice)
                        .changePercent(changePercent)
                        .volume(volume)
                        .amount(amount)
                        .highPrice(new BigDecimal(fields[33]))
                        .lowPrice(new BigDecimal(fields[34]))
                        .openPrice(new BigDecimal(fields[5]))
                        .preClose(preClose)
                        .volumeDisplay(formatVolume(volume))
                        .amountDisplay(formatAmount(amount))
                        .build();
                
                result.add(dto);
            }
        } catch (Exception e) {
            log.error("解析腾讯实时数据到DTOFailed: {}", e.getMessage());
        }
        return result;
    }
    
    private String formatVolume(Long volume) {
        if (volume == null || volume == 0) {
            return "0手";
        }
        // 转换为万手
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

    /**
     * 获取实时行情数据（批量）- 更新到数据库
     */
    @Transactional
    public void updateRealtimeDataBatch() {
        try {
            List<Stock> stocks = stockRepository.findByStatus(1);
            if (stocks.isEmpty()) return;

            // 分批处理，每批50只
            int batchSize = 50;
            for (int i = 0; i < stocks.size(); i += batchSize) {
                List<Stock> batch = stocks.subList(i, Math.min(i + batchSize, stocks.size()));
                processBatch(batch);
                Thread.sleep(100); // 避免请求过快
            }
        } catch (Exception e) {
            log.error("批量Update实时数据Failed: {}", e.getMessage());
        }
    }

    private void processBatch(List<Stock> stocks) {
        try {
            String symbols = stocks.stream()
                    .map(s -> convertToTencentSymbol(s.getSymbol()))
                    .collect(Collectors.joining(","));

            String url = TENCENT_REALTIME_API + symbols;
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Referer", "https://qt.gtimg.cn");
            headers.set("Accept", "*/*");
            headers.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            headers.set("Accept-Encoding", "gzip, deflate, br");
            headers.set("Connection", "keep-alive");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            if (response.getBody() != null) {
                parseTencentRealtimeData(response.getBody(), stocks);
            }
        } catch (Exception e) {
            log.warn("腾讯API调用Failed，使用本地数据: {}", e.getMessage());
            // API调用失败时使用本地模拟数据更新
            generateLocalRealtimeData(stocks);
        }
    }

    /**
     * 当外部API不可用时，生成本地模拟数据
     */
    private void generateLocalRealtimeData(List<Stock> stocks) {
        for (Stock stock : stocks) {
            try {
                StockRealtimeData realtimeData = realtimeDataRepository
                        .findByStockId(stock.getId())
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
                
                // 生成买卖五档数据
                for (int i = 1; i <= 5; i++) {
                    BigDecimal bidPrice = currentPrice.multiply(BigDecimal.valueOf(1 - i * 0.001)).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal askPrice = currentPrice.multiply(BigDecimal.valueOf(1 + i * 0.001)).setScale(2, RoundingMode.HALF_UP);
                    long volume = (long) (Math.random() * 10000);
                    
                    setBidAskData(realtimeData, i, bidPrice, volume, askPrice, volume);
                }
                
                realtimeDataRepository.save(realtimeData);
            } catch (Exception ex) {
                log.error("生成本地实时数据Failed {}: {}", stock.getSymbol(), ex.getMessage());
            }
        }
    }

    private void setBidAskData(StockRealtimeData data, int level, BigDecimal bidPrice, long bidVolume, 
                               BigDecimal askPrice, long askVolume) {
        switch (level) {
            case 1:
                data.setBidPrice1(bidPrice); data.setBidVolume1(bidVolume);
                data.setAskPrice1(askPrice); data.setAskVolume1(askVolume);
                break;
            case 2:
                data.setBidPrice2(bidPrice); data.setBidVolume2(bidVolume);
                data.setAskPrice2(askPrice); data.setAskVolume2(askVolume);
                break;
            case 3:
                data.setBidPrice3(bidPrice); data.setBidVolume3(bidVolume);
                data.setAskPrice3(askPrice); data.setAskVolume3(askVolume);
                break;
            case 4:
                data.setBidPrice4(bidPrice); data.setBidVolume4(bidVolume);
                data.setAskPrice4(askPrice); data.setAskVolume4(askVolume);
                break;
            case 5:
                data.setBidPrice5(bidPrice); data.setBidVolume5(bidVolume);
                data.setAskPrice5(askPrice); data.setAskVolume5(askVolume);
                break;
        }
    }

    private void parseTencentRealtimeData(String data, List<Stock> stocks) {
        try {
            // 腾讯返回格式: v_sh600000="1~浦发银行~600000~..."
            String[] lines = data.split(";");
            Map<String, Stock> stockMap = stocks.stream()
                    .collect(Collectors.toMap(Stock::getSymbol, s -> s));

            for (String line : lines) {
                if (line.trim().isEmpty()) continue;

                int start = line.indexOf('"');
                int end = line.lastIndexOf('"');
                if (start == -1 || end == -1 || start >= end) continue;

                String content = line.substring(start + 1, end);
                String[] fields = content.split("~");
                if (fields.length < 45) continue;

                String symbol = fields[2];
                Stock stock = stockMap.get(symbol);
                if (stock == null) continue;

                StockRealtimeData realtimeData = realtimeDataRepository
                        .findByStockId(stock.getId())
                        .orElse(new StockRealtimeData());

                realtimeData.setStock(stock);
                realtimeData.setCurrentPrice(new BigDecimal(fields[3]));
                realtimeData.setPreClose(new BigDecimal(fields[4]));
                realtimeData.setOpenPrice(new BigDecimal(fields[5]));
                realtimeData.setHighPrice(new BigDecimal(fields[33]));
                realtimeData.setLowPrice(new BigDecimal(fields[34]));
                realtimeData.setVolume(Long.parseLong(fields[36]) / 100); // 手转股
                realtimeData.setAmount(new BigDecimal(fields[37]).multiply(new BigDecimal("10000")));

                // 计算涨跌幅
                BigDecimal changePrice = realtimeData.getCurrentPrice()
                        .subtract(realtimeData.getPreClose());
                BigDecimal changePercent = changePrice.divide(realtimeData.getPreClose(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                realtimeData.setChangePrice(changePrice);
                realtimeData.setChangePercent(changePercent);

                // 买卖五档
                realtimeData.setBidPrice1(new BigDecimal(fields[9]));
                realtimeData.setBidVolume1(Long.parseLong(fields[10]));
                realtimeData.setBidPrice2(new BigDecimal(fields[11]));
                realtimeData.setBidVolume2(Long.parseLong(fields[12]));
                realtimeData.setBidPrice3(new BigDecimal(fields[13]));
                realtimeData.setBidVolume3(Long.parseLong(fields[14]));
                realtimeData.setBidPrice4(new BigDecimal(fields[15]));
                realtimeData.setBidVolume4(Long.parseLong(fields[16]));
                realtimeData.setBidPrice5(new BigDecimal(fields[17]));
                realtimeData.setBidVolume5(Long.parseLong(fields[18]));

                realtimeData.setAskPrice1(new BigDecimal(fields[19]));
                realtimeData.setAskVolume1(Long.parseLong(fields[20]));
                realtimeData.setAskPrice2(new BigDecimal(fields[21]));
                realtimeData.setAskVolume2(Long.parseLong(fields[22]));
                realtimeData.setAskPrice3(new BigDecimal(fields[23]));
                realtimeData.setAskVolume3(Long.parseLong(fields[24]));
                realtimeData.setAskPrice4(new BigDecimal(fields[25]));
                realtimeData.setAskVolume4(Long.parseLong(fields[26]));
                realtimeData.setAskPrice5(new BigDecimal(fields[27]));
                realtimeData.setAskVolume5(Long.parseLong(fields[28]));

                realtimeDataRepository.save(realtimeData);
            }
        } catch (Exception e) {
            log.error("解析腾讯实时数据Failed: {}", e.getMessage());
        }
    }

    /**
     * 获取K线历史数据
     */
    public List<KlineDataDTO> getKlineDataFromTencent(String symbol, String period) {
        List<KlineDataDTO> result = new ArrayList<>();
        try {
            String tencentSymbol = convertToTencentSymbol(symbol);
            String klineType = convertPeriodToKlineType(period);

            String url = String.format("%s?param=%s,%s,,,%s", 
                    TENCENT_KLINE_API, tencentSymbol, klineType, getLimitByPeriod(period));

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            headers.set("Referer", "https://stockpage.10jqka.com.cn");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            if (response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode data = root.path("data").path(tencentSymbol).path(klineType);

                if (data.isArray()) {
                    for (JsonNode item : data) {
                        KlineDataDTO dto = new KlineDataDTO();
                        dto.setDate(LocalDate.parse(item.get(0).asText(), 
                                DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                        dto.setOpen(new BigDecimal(item.get(1).asText()));
                        dto.setClose(new BigDecimal(item.get(2).asText()));
                        dto.setLow(new BigDecimal(item.get(3).asText()));
                        dto.setHigh(new BigDecimal(item.get(4).asText()));
                        dto.setVolume(item.get(5).asLong());

                        // 计算涨跌幅
                        BigDecimal changePercent = dto.getClose().subtract(dto.getOpen())
                                .divide(dto.getOpen(), 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));
                        dto.setChangePercent(changePercent);

                        result.add(dto);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Get腾讯K线数据Failed: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 获取分时数据
     */
    public List<MinuteDataDTO> getMinuteData(String symbol) {
        List<MinuteDataDTO> result = new ArrayList<>();
        try {
            String tencentSymbol = convertToTencentSymbol(symbol);
            String url = TENCENT_MINUTE_API + "?code=" + tencentSymbol;

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            if (response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode data = root.path("data").path(tencentSymbol).path("data");

                String[] lines = data.asText().split("\\|");
                for (String line : lines) {
                    String[] fields = line.split(" ");
                    if (fields.length >= 3) {
                        MinuteDataDTO dto = new MinuteDataDTO();
                        dto.setTime(fields[0]);
                        dto.setPrice(new BigDecimal(fields[1]));
                        dto.setVolume(Long.parseLong(fields[2]));
                        result.add(dto);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Get分时数据Failed: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 获取单只股票实时数据
     */
    @Nullable
    public StockRealtimeData getRealtimeData(String symbol) {
        try {
            String tencentSymbol = convertToTencentSymbol(symbol);
            String url = TENCENT_REALTIME_API + tencentSymbol;

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Referer", "https://qt.gtimg.cn");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            if (response.getBody() != null) {
                return parseRealtimeData(response.getBody(), symbol);
            }
        } catch (Exception e) {
            log.warn("从腾讯Get股票 {} 实时数据Failed: {}", symbol, e.getMessage());
        }
        return null;
    }

    /**
     * 获取股票完整信息（包含股本、财务指标等）
     * 使用腾讯API的详细字段
     */
    @Nullable
    public com.stock.platform.dto.StockInfoDTO getStockFullInfo(String symbol) {
        try {
            String tencentSymbol = convertToTencentSymbol(symbol);
            // 使用腾讯详细数据API
            String url = TENCENT_REALTIME_API + tencentSymbol;

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Referer", "https://qt.gtimg.cn");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            if (response.getBody() != null) {
                return parseFullStockInfo(response.getBody(), symbol);
            }
        } catch (Exception e) {
            log.warn("从腾讯Get股票 {} 完整InfoFailed: {}", symbol, e.getMessage());
        }
        return null;
    }

    @Nullable
    private com.stock.platform.dto.StockInfoDTO parseFullStockInfo(String data, String symbol) {
        try {
            String[] lines = data.split(";");
            for (String line : lines) {
                if (line.contains("=")) {
                    String[] parts = line.split("=");
                    if (parts.length >= 2) {
                        String valueStr = parts[1].replace("\"", "").trim();
                        String[] fields = valueStr.split("~");

                        // 腾讯API字段说明（详细版）:
                        // fields[0]=未知, fields[1]=股票名称, fields[2]=股票代码, fields[3]=当前价格
                        // fields[4]=昨收, fields[5]=今开, fields[6]=成交量(手), fields[7]=外盘, fields[8]=内盘
                        // fields[9-18]=买1-5价格和数量, fields[19-28]=卖1-5价格和数量
                        // fields[29]=最近逐笔成交, fields[30]=时间, fields[31]=涨跌, fields[32]=涨跌幅%
                        // fields[33]=最高, fields[34]=最低, fields[35]=价格/成交量(手)/成交额
                        // fields[36]=成交量(手), fields[37]=成交额(万), fields[38]=换手率%
                        // fields[39]=市盈率, fields[40]=未知, fields[41]=最高, fields[42]=最低
                        // fields[43]=振幅%, fields[44]=流通市值(亿), fields[45]=总市值(亿)
                        // fields[46]=市净率, fields[47]=涨停价, fields[48]=跌停价
                        // fields[49-50]=未知, fields[51]=总股本(亿), fields[52]=流通股本(亿)

                        if (fields.length >= 53) {
                            com.stock.platform.dto.StockInfoDTO.StockInfoDTOBuilder builder = 
                                com.stock.platform.dto.StockInfoDTO.builder()
                                    .symbol(symbol)
                                    .name(fields[1])
                                    .currentPrice(new BigDecimal(fields[3]))
                                    .preClose(new BigDecimal(fields[4]))
                                    .openPrice(new BigDecimal(fields[5]))
                                    .highPrice(new BigDecimal(fields[33]))
                                    .lowPrice(new BigDecimal(fields[34]))
                                    .volume(Long.parseLong(fields[36]) * 100)  // 手转股
                                    .amount(new BigDecimal(fields[37]).multiply(new BigDecimal("10000")));

                            // 使用腾讯返回的涨跌额和涨跌幅
                            // fields[31] = 涨跌, fields[32] = 涨跌幅%
                            if (fields.length > 31 && !fields[31].isEmpty()) {
                                builder.changePrice(new BigDecimal(fields[31]));
                            } else {
                                // 如果没有返回，自己计算
                                BigDecimal changePrice = new BigDecimal(fields[3]).subtract(new BigDecimal(fields[4]));
                                builder.changePrice(changePrice);
                            }

                            if (fields.length > 32 && !fields[32].isEmpty()) {
                                builder.changePercent(new BigDecimal(fields[32]));
                            } else {
                                // 如果没有返回，自己计算
                                BigDecimal changePercent = builder.build().getChangePrice().multiply(BigDecimal.valueOf(100))
                                        .divide(new BigDecimal(fields[4]), 2, RoundingMode.HALF_UP);
                                builder.changePercent(changePercent);
                            }

                            // 股本信息（亿转股）
                            if (!fields[51].isEmpty()) {
                                builder.totalShares(new BigDecimal(fields[51]).multiply(new BigDecimal("100000000")).longValue());
                            }
                            if (!fields[52].isEmpty()) {
                                builder.floatShares(new BigDecimal(fields[52]).multiply(new BigDecimal("100000000")).longValue());
                            }

                            // 市值（已经是亿单位，直接保存）
                            if (!fields[45].isEmpty()) {
                                builder.totalMarketCap(new BigDecimal(fields[45]));
                            }
                            if (!fields[44].isEmpty()) {
                                builder.floatMarketCap(new BigDecimal(fields[44]));
                            }

                            // 财务指标
                            if (!fields[39].isEmpty()) {
                                builder.peRatio(new BigDecimal(fields[39]));
                            }
                            if (!fields[46].isEmpty()) {
                                builder.pbRatio(new BigDecimal(fields[46]));
                            }

                            // 交易指标
                            if (!fields[38].isEmpty()) {
                                builder.turnoverRate(new BigDecimal(fields[38]));
                            }
                            if (!fields[43].isEmpty()) {
                                builder.amplitude(new BigDecimal(fields[43]));
                            }
                            if (!fields[47].isEmpty()) {
                                builder.limitUpPrice(new BigDecimal(fields[47]));
                            }
                            if (!fields[48].isEmpty()) {
                                builder.limitDownPrice(new BigDecimal(fields[48]));
                            }

                            return builder.build();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析腾讯完整股票InfoFailed: {}", e.getMessage());
        }
        return null;
    }

    @Nullable
    private StockRealtimeData parseRealtimeData(String data, String symbol) {
        try {
            String[] lines = data.split(";");
            for (String line : lines) {
                if (line.contains("=")) {
                    String[] parts = line.split("=");
                    if (parts.length >= 2) {
                        String valueStr = parts[1].replace("\"", "").trim();
                        String[] fields = valueStr.split("~");

                        // 腾讯API字段说明:
                        // fields[0]=未知, fields[1]=股票名称, fields[2]=股票代码, fields[3]=当前价格
                        // fields[4]=昨收, fields[5]=今开, fields[6]=成交量(手), fields[7]=外盘, fields[8]=内盘
                        // fields[9-18]=买1-5价格和数量, fields[19-28]=卖1-5价格和数量
                        // fields[29]=最近逐笔成交, fields[30]=时间, fields[31]=涨跌, fields[32]=涨跌幅%
                        // fields[33]=最高, fields[34]=最低, fields[35]=价格/成交量(手)/成交额
                        // fields[36]=成交量(手), fields[37]=成交额(万), fields[38]=换手率%
                        // fields[39]=市盈率, fields[40]=未知, fields[41]=最高, fields[42]=最低
                        // fields[43]=振幅%, fields[44]=流通市值(亿), fields[45]=总市值(亿)
                        // fields[46]=市净率, fields[47]=涨停价, fields[48]=跌停价

                        if (fields.length >= 49) {
                            StockRealtimeData realtimeData = new StockRealtimeData();
                            realtimeData.setCurrentPrice(new BigDecimal(fields[3]));
                            realtimeData.setPreClose(new BigDecimal(fields[4]));
                            realtimeData.setOpenPrice(new BigDecimal(fields[5]));
                            realtimeData.setHighPrice(new BigDecimal(fields[33]));
                            realtimeData.setLowPrice(new BigDecimal(fields[34]));
                            realtimeData.setVolume(Long.parseLong(fields[36]) * 100);  // 手转股
                            realtimeData.setAmount(new BigDecimal(fields[37]).multiply(new BigDecimal("10000")));

                            // 使用腾讯返回的涨跌额和涨跌幅
                            // fields[31] = 涨跌, fields[32] = 涨跌幅%
                            if (fields.length > 31 && !fields[31].isEmpty()) {
                                realtimeData.setChangePrice(new BigDecimal(fields[31]));
                            } else {
                                // 如果没有返回，自己计算
                                BigDecimal changePrice = new BigDecimal(fields[3]).subtract(new BigDecimal(fields[4]));
                                realtimeData.setChangePrice(changePrice);
                            }

                            if (fields.length > 32 && !fields[32].isEmpty()) {
                                realtimeData.setChangePercent(new BigDecimal(fields[32]));
                            } else {
                                // 如果没有返回，自己计算
                                BigDecimal changePercent = realtimeData.getChangePrice().multiply(BigDecimal.valueOf(100))
                                        .divide(new BigDecimal(fields[4]), 2, RoundingMode.HALF_UP);
                                realtimeData.setChangePercent(changePercent);
                            }

                            // 买卖五档
                            realtimeData.setBidPrice1(parseDecimal(fields[9]));
                            realtimeData.setBidVolume1(parseLong(fields[10]) * 100);
                            realtimeData.setBidPrice2(parseDecimal(fields[11]));
                            realtimeData.setBidVolume2(parseLong(fields[12]) * 100);
                            realtimeData.setBidPrice3(parseDecimal(fields[13]));
                            realtimeData.setBidVolume3(parseLong(fields[14]) * 100);
                            realtimeData.setBidPrice4(parseDecimal(fields[15]));
                            realtimeData.setBidVolume4(parseLong(fields[16]) * 100);
                            realtimeData.setBidPrice5(parseDecimal(fields[17]));
                            realtimeData.setBidVolume5(parseLong(fields[18]) * 100);

                            realtimeData.setAskPrice1(parseDecimal(fields[19]));
                            realtimeData.setAskVolume1(parseLong(fields[20]) * 100);
                            realtimeData.setAskPrice2(parseDecimal(fields[21]));
                            realtimeData.setAskVolume2(parseLong(fields[22]) * 100);
                            realtimeData.setAskPrice3(parseDecimal(fields[23]));
                            realtimeData.setAskVolume3(parseLong(fields[24]) * 100);
                            realtimeData.setAskPrice4(parseDecimal(fields[25]));
                            realtimeData.setAskVolume4(parseLong(fields[26]) * 100);
                            realtimeData.setAskPrice5(parseDecimal(fields[27]));
                            realtimeData.setAskVolume5(parseLong(fields[28]) * 100);

                            realtimeData.setUpdateTime(LocalDateTime.now());

                            return realtimeData;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析腾讯实时数据Failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取大盘指数数据
     */
    @Nullable
    public MarketIndexData getMarketIndex(String indexCode) {
        try {
            // 腾讯格式: sh000001, sz399001, sz399006, sh000688
            String tencentCode = convertIndexToTencentCode(indexCode);
            String url = TENCENT_REALTIME_API + tencentCode;

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Referer", "https://qt.gtimg.cn");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            if (response.getBody() != null) {
                // 解析腾讯返回的数据
                String data = response.getBody();
                String[] lines = data.split(";");
                for (String line : lines) {
                    if (line.contains("=")) {
                        String[] parts = line.split("=");
                        if (parts.length >= 2) {
                            String valueStr = parts[1].replace("\"", "").trim();
                            String[] fields = valueStr.split("~");

                            if (fields.length >= 35) {
                                MarketIndexData indexData = new MarketIndexData();
                                // 大盘指数数据格式与个股不同！
                                // fields[3] = 当前价格
                                // fields[4] = 昨收
                                // fields[32] = 涨跌额 (注意：大盘是32，个股是31)
                                // fields[33] = 涨跌幅(%) (注意：大盘是33，个股是32)
                                indexData.setCurrentPrice(new BigDecimal(fields[3]));
                                indexData.setPreClose(new BigDecimal(fields[4]));

                                // 直接使用腾讯返回的涨跌额和涨跌幅
                                // 大盘指数：fields[32]=涨跌额, fields[33]=涨跌幅%
                                if (fields.length > 32 && !fields[32].isEmpty()) {
                                    indexData.setChangePrice(new BigDecimal(fields[32]));
                                } else {
                                    // 如果没有返回，自己计算
                                    BigDecimal changePrice = indexData.getCurrentPrice()
                                            .subtract(indexData.getPreClose());
                                    indexData.setChangePrice(changePrice);
                                }

                                if (fields.length > 33 && !fields[33].isEmpty()) {
                                    indexData.setChangePercent(new BigDecimal(fields[33]));
                                } else {
                                    // 如果没有返回，自己计算
                                    BigDecimal changePercent = indexData.getChangePrice()
                                            .multiply(BigDecimal.valueOf(100))
                                            .divide(indexData.getPreClose(), 2, RoundingMode.HALF_UP);
                                    indexData.setChangePercent(changePercent);
                                }

                                return indexData;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("从腾讯Get大盘指数 {} Failed: {}", indexCode, e.getMessage());
        }
        return null;
    }

    private String convertIndexToTencentCode(String indexCode) {
        // 转换东方财富格式到腾讯格式
        // 1.000001 -> sh000001
        // 0.399001 -> sz399001
        if (indexCode.startsWith("1.")) {
            return "sh" + indexCode.substring(2);
        } else if (indexCode.startsWith("0.")) {
            return "sz" + indexCode.substring(2);
        }
        return indexCode;
    }

    private String convertToTencentSymbol(String symbol) {
        if (symbol.startsWith("6")) {
            return "sh" + symbol;
        } else if (symbol.startsWith("0") || symbol.startsWith("3")) {
            return "sz" + symbol;
        }
        return symbol;
    }

    private String convertPeriodToKlineType(String period) {
        switch (period) {
            case "day": return "day";
            case "week": return "week";
            case "month": return "month";
            default: return "day";
        }
    }

    private int getLimitByPeriod(String period) {
        switch (period) {
            case "1m": return 30;
            case "3m": return 90;
            case "6m": return 180;
            case "1y": return 365;
            default: return 100;
        }
    }

    /**
     * 安全解析 BigDecimal
     */
    private BigDecimal parseDecimal(String value) {
        try {
            if (value == null || value.isEmpty()) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 安全解析 Long
     */
    private Long parseLong(String value) {
        try {
            if (value == null || value.isEmpty()) {
                return 0L;
            }
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
