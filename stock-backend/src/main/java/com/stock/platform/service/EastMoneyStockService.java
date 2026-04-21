package com.stock.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.platform.dto.KlineDataDTO;
import com.stock.platform.dto.MinuteDataDTO;
import com.stock.platform.entity.StockRealtimeData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 东方财富股票数据服务
 * 提供稳定的国内股票数据API
 */
@Service
@Slf4j
public class EastMoneyStockService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 东方财富API
    private static final String EASTMONEY_REALTIME_API = "https://push2.eastmoney.com/api/qt/stock/get";
    private static final String EASTMONEY_KLINE_API = "https://push2his.eastmoney.com/api/qt/stock/kline/get";
    private static final String EASTMONEY_MINUTE_API = "https://push2.eastmoney.com/api/qt/stock/trends2/get";

    /**
     * 获取K线数据
     */
    public List<KlineDataDTO> getKlineData(String symbol, String period) {
        List<KlineDataDTO> result = new ArrayList<>();
        try {
            String secId = convertToSecId(symbol);
            String klt = convertPeriodToKlt(period);
            int lmt = getLimitByPeriod(period);

            String url = String.format(
                "%s?secid=%s&fields1=f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11,f12,f13&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61&klt=%s&fqt=0&end=20500101&lmt=%d",
                EASTMONEY_KLINE_API, secId, klt, lmt
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Referer", "https://quote.eastmoney.com");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            if (response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode klines = root.path("data").path("klines");

                if (klines.isArray()) {
                    for (JsonNode kline : klines) {
                        String[] parts = kline.asText().split(",");
                        if (parts.length >= 6) {
                            KlineDataDTO dto = new KlineDataDTO();
                            dto.setDate(LocalDate.parse(parts[0], DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                            dto.setOpen(new BigDecimal(parts[1]));
                            dto.setClose(new BigDecimal(parts[2]));
                            dto.setHigh(new BigDecimal(parts[3]));
                            dto.setLow(new BigDecimal(parts[4]));
                            dto.setVolume(Long.parseLong(parts[5]));
                            dto.setAmount(parts.length > 6 ? new BigDecimal(parts[6]) : BigDecimal.ZERO);
                            
                            // 计算涨跌幅
                            BigDecimal changePercent = dto.getClose().subtract(dto.getOpen())
                                    .divide(dto.getOpen(), 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100));
                            dto.setChangePercent(changePercent);
                            
                            result.add(dto);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("东方财富K线数据GetFailed: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 获取分时数据
     */
    public List<MinuteDataDTO> getMinuteData(String symbol) {
        List<MinuteDataDTO> result = new ArrayList<>();
        try {
            // 检查是否开市时间
            if (!isTradingTime()) {
                log.debug("当前非交易时间，返回空的分时数据");
                return result;
            }

            String secId = convertToSecId(symbol);
            
            String url = String.format(
                "%s?secid=%s&fields1=f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11,f12,f13&fields2=f51,f52,f53,f54,f55,f56,f57,f58&iscr=0&iscca=0&ut=fa5fd1943c7b386f172d6893dbfba10b",
                EASTMONEY_MINUTE_API, secId
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Referer", "https://quote.eastmoney.com");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            if (response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode trends = root.path("data").path("trends");

                if (trends.isArray()) {
                    for (JsonNode trend : trends) {
                        String[] parts = trend.asText().split(",");
                        if (parts.length >= 3) {
                            MinuteDataDTO dto = new MinuteDataDTO();
                            dto.setTime(parts[0]); // 格式: 09:30
                            // 东方财富价格是以"分"为单位的整数，需要除以100
                            dto.setPrice(new BigDecimal(parts[1]).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                            dto.setVolume(Long.parseLong(parts[2]));
                            if (parts.length > 3) {
                                dto.setAvgPrice(new BigDecimal(parts[3]).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                            }
                            result.add(dto);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("东方财富分时数据GetFailed: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 判断当前是否为交易时间
     * 交易时间：周一至周五 9:30-11:30, 13:00-15:00
     */
    private boolean isTradingTime() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.DayOfWeek dayOfWeek = now.getDayOfWeek();
        
        // 周末不开市
        if (dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY) {
            return false;
        }
        
        java.time.LocalTime time = now.toLocalTime();
        
        // 上午交易时间：9:30-11:30
        boolean morningSession = !time.isBefore(java.time.LocalTime.of(9, 30)) && 
                                  !time.isAfter(java.time.LocalTime.of(11, 30));
        
        // 下午交易时间：13:00-15:00
        boolean afternoonSession = !time.isBefore(java.time.LocalTime.of(13, 0)) && 
                                    !time.isAfter(java.time.LocalTime.of(15, 0));
        
        return morningSession || afternoonSession;
    }

    /**
     * 获取实时股票数据（单只股票）
     */
    public StockRealtimeData getRealtimeData(String symbol) {
        try {
            String secId = convertToSecId(symbol);
            String url = String.format(
                "%s?secid=%s&fields=f43,f44,f45,f46,f47,f48,f49,f50,f51,f52,f57,f58,f60,f107,f108,f109,f110,f111,f112,f113,f114,f115,f116,f117,f118,f119,f120,f121,f122,f123,f124,f125,f126,f127,f128,f129,f130,f131,f132,f133,f134,f135,f136,f137,f138,f139,f140,f141,f142,f143,f144,f145,f146,f147,f148,f149,f150,f151,f152,f153,f154,f155,f156,f157,f158,f159,f160,f161,f162,f163,f164,f165,f166,f167,f168,f169,f170,f171,f172,f173,f174,f175,f176,f177,f178,f179,f180,f181,f182,f183,f184,f185,f186,f187,f188,f189,f190,f191,f192,f193,f194,f195,f196,f197,f198,f199,f200",
                EASTMONEY_REALTIME_API, secId
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Referer", "https://quote.eastmoney.com");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            if (response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode data = root.path("data");

                if (!data.isMissingNode()) {
                    StockRealtimeData realtimeData = new StockRealtimeData();

                    // 解析实时数据字段
                    // 东方财富API返回的价格是以"分"为单位的整数，需要除以100
                    BigDecimal currentPrice = parseBigDecimal(data.get("f43")); // 最新价
                    BigDecimal preClose = parseBigDecimal(data.get("f60")); // 昨收
                    BigDecimal openPrice = parseBigDecimal(data.get("f46")); // 今开
                    BigDecimal highPrice = parseBigDecimal(data.get("f44")); // 最高
                    BigDecimal lowPrice = parseBigDecimal(data.get("f45")); // 最低
                    Long volume = parseLong(data.get("f47")); // 成交量
                    BigDecimal amount = parseBigDecimal(data.get("f48")); // 成交额

                    // 除以100转换为元
                    if (currentPrice != null) {
                        currentPrice = currentPrice.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    }
                    if (preClose != null) {
                        preClose = preClose.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    }
                    if (openPrice != null) {
                        openPrice = openPrice.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    }
                    if (highPrice != null) {
                        highPrice = highPrice.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    }
                    if (lowPrice != null) {
                        lowPrice = lowPrice.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    }

                    realtimeData.setCurrentPrice(currentPrice);
                    realtimeData.setPreClose(preClose);
                    realtimeData.setOpenPrice(openPrice);
                    realtimeData.setHighPrice(highPrice);
                    realtimeData.setLowPrice(lowPrice);
                    realtimeData.setVolume(volume);
                    realtimeData.setAmount(amount);

                    // 计算涨跌幅
                    if (currentPrice != null && preClose != null && preClose.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal changePrice = currentPrice.subtract(preClose);
                        BigDecimal changePercent = changePrice.multiply(BigDecimal.valueOf(100))
                                .divide(preClose, 2, RoundingMode.HALF_UP);
                        realtimeData.setChangePrice(changePrice);
                        realtimeData.setChangePercent(changePercent);
                    }

                    // 买卖五档价格也需要除以100
                    realtimeData.setBidPrice1(divideBy100(parseBigDecimal(data.get("f31"))));
                    realtimeData.setBidVolume1(parseLong(data.get("f32")));
                    realtimeData.setBidPrice2(divideBy100(parseBigDecimal(data.get("f33"))));
                    realtimeData.setBidVolume2(parseLong(data.get("f34")));
                    realtimeData.setBidPrice3(divideBy100(parseBigDecimal(data.get("f35"))));
                    realtimeData.setBidVolume3(parseLong(data.get("f36")));
                    realtimeData.setBidPrice4(divideBy100(parseBigDecimal(data.get("f37"))));
                    realtimeData.setBidVolume4(parseLong(data.get("f38")));
                    realtimeData.setBidPrice5(divideBy100(parseBigDecimal(data.get("f39"))));
                    realtimeData.setBidVolume5(parseLong(data.get("f40")));

                    realtimeData.setAskPrice1(divideBy100(parseBigDecimal(data.get("f19"))));
                    realtimeData.setAskVolume1(parseLong(data.get("f20")));
                    realtimeData.setAskPrice2(divideBy100(parseBigDecimal(data.get("f21"))));
                    realtimeData.setAskVolume2(parseLong(data.get("f22")));
                    realtimeData.setAskPrice3(divideBy100(parseBigDecimal(data.get("f23"))));
                    realtimeData.setAskVolume3(parseLong(data.get("f24")));
                    realtimeData.setAskPrice4(divideBy100(parseBigDecimal(data.get("f25"))));
                    realtimeData.setAskVolume4(parseLong(data.get("f26")));
                    realtimeData.setAskPrice5(divideBy100(parseBigDecimal(data.get("f27"))));
                    realtimeData.setAskVolume5(parseLong(data.get("f28")));

                    return realtimeData;
                }
            }
        } catch (Exception e) {
            log.warn("Get股票 {} 实时数据Failed: {}", symbol, e.getMessage());
        }
        return null;
    }

    /**
     * 获取大盘指数实时数据
     */
    public MarketIndexData getMarketIndex(String indexCode) {
        try {
            // 上证指数: 1.000001, 深证成指: 0.399001, 创业板指: 0.399006, 科创50: 1.000688
            String secId = indexCode;
            String url = String.format(
                "%s?secid=%s&fields=f43,f44,f45,f46,f47,f48,f49,f50,f51,f52,f57,f58,f60,f107,f108,f109,f110",
                EASTMONEY_REALTIME_API, secId
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Referer", "https://quote.eastmoney.com");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            if (response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode data = root.path("data");

                if (!data.isMissingNode()) {
                    MarketIndexData indexData = new MarketIndexData();

                    // 东方财富API返回的价格是以"分"为单位的整数，需要除以100
                    BigDecimal currentPrice = parseBigDecimal(data.get("f43"));
                    BigDecimal preClose = parseBigDecimal(data.get("f60"));

                    if (currentPrice != null && preClose != null && preClose.compareTo(BigDecimal.ZERO) > 0) {
                        // 计算涨跌额（分）
                        BigDecimal changePriceInFen = currentPrice.subtract(preClose);
                        // 计算涨跌幅（基于分的价格）
                        BigDecimal changePercent = changePriceInFen.multiply(BigDecimal.valueOf(100))
                                .divide(preClose, 2, RoundingMode.HALF_UP);
                        
                        // 转换为元
                        if (currentPrice != null) {
                            currentPrice = currentPrice.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                        }
                        if (preClose != null) {
                            preClose = preClose.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                        }
                        // 转换涨跌额为元
                        BigDecimal changePriceInYuan = changePriceInFen.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                        indexData.setCurrentPrice(currentPrice);
                        indexData.setPreClose(preClose);
                        indexData.setChangePrice(changePriceInYuan);
                        indexData.setChangePercent(changePercent);
                    }

                    return indexData;
                }
            }
        } catch (Exception e) {
            log.warn("Get大盘指数 {} Failed: {}", indexCode, e.getMessage());
        }
        return null;
    }

    private BigDecimal parseBigDecimal(JsonNode node) {
        if (node == null || node.isNull()) return null;
        try {
            return new BigDecimal(node.asText());
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseLong(JsonNode node) {
        if (node == null || node.isNull()) return null;
        try {
            return node.asLong();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将分转换为元（除以100）
     */
    private BigDecimal divideBy100(BigDecimal value) {
        if (value == null) return null;
        return value.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * 转换股票代码为东方财富格式
     */
    private String convertToSecId(String symbol) {
        if (symbol.startsWith("6")) {
            return "1." + symbol; // 上海
        } else if (symbol.startsWith("0") || symbol.startsWith("3")) {
            return "0." + symbol; // 深圳
        }
        return "0." + symbol;
    }

    /**
     * 转换周期为东方财富格式
     */
    private String convertPeriodToKlt(String period) {
        switch (period.toLowerCase()) {
            case "day": return "101"; // 日线
            case "week": return "102"; // 周线
            case "month": return "103"; // 月线
            case "1m": return "101"; // 1月
            case "3m": return "101"; // 3月
            case "6m": return "101"; // 6月
            case "1y": return "101"; // 1年
            default: return "101";
        }
    }

    private int getLimitByPeriod(String period) {
        switch (period.toLowerCase()) {
            case "1m": return 30;
            case "3m": return 90;
            case "6m": return 180;
            case "1y": return 365;
            default: return 100;
        }
    }
}
