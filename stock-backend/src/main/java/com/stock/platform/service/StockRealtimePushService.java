package com.stock.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.platform.dto.StockDTO;
import com.stock.platform.dto.StockDetailDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 股票实时数据按需推送服务
 * 根据前端订阅的股票列表，实时获取并推送数据
 */
@Slf4j
@Service
public class StockRealtimePushService {

    @Autowired
    private TencentStockDataService tencentStockDataService;

    @Autowired
    private StockDataService stockDataService;

    @Autowired
    private ObjectMapper objectMapper;

    // 存储客户端订阅的股票列表
    private final Map<String, Set<String>> clientSubscriptions = new ConcurrentHashMap<>();

    // 存储所有需要获取的股票（合并所有客户端的订阅）
    private final Set<String> allSubscribedSymbols = ConcurrentHashMap.newKeySet();

    // 推送线程池
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // 推送任务
    private ScheduledFuture<?> pushTask;

    // 数据缓存（减少API调用）
    private final Map<String, StockDTO> stockCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamp = new ConcurrentHashMap<>();
    private static final long CACHE_TTL = 3000; // 缓存3秒

    @PostConstruct
    public void init() {
        // 每2秒执行一次推送
        pushTask = scheduler.scheduleAtFixedRate(this::fetchAndPushData, 3, 2, TimeUnit.SECONDS);
        log.info("Stock realtime push service started");
    }

    @PreDestroy
    public void destroy() {
        if (pushTask != null) {
            pushTask.cancel(false);
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        log.info("股票实时推送服务已停止");
    }

    /**
     * 客户端订阅股票
     */
    public void subscribe(String sessionId, Set<String> symbols) {
        clientSubscriptions.put(sessionId, ConcurrentHashMap.newKeySet());
        clientSubscriptions.get(sessionId).addAll(symbols);
        updateAllSubscribedSymbols();
        log.info("Client {} subscribed {} stocks: {}", sessionId, symbols.size(), symbols);
    }

    /**
     * 客户端取消订阅
     */
    public void unsubscribe(String sessionId, Set<String> symbols) {
        Set<String> subs = clientSubscriptions.get(sessionId);
        if (subs != null) {
            subs.removeAll(symbols);
            updateAllSubscribedSymbols();
            log.info("Client {} 取消subscribed {} stocks", sessionId, symbols.size());
        }
    }

    /**
     * 客户端断开连接
     */
    public void clientDisconnected(String sessionId) {
        clientSubscriptions.remove(sessionId);
        updateAllSubscribedSymbols();
        log.info("Client {} disconnected", sessionId);
    }

    /**
     * 更新所有订阅的股票列表
     */
    private void updateAllSubscribedSymbols() {
        allSubscribedSymbols.clear();
        for (Set<String> symbols : clientSubscriptions.values()) {
            allSubscribedSymbols.addAll(symbols);
        }
    }

    /**
     * 获取并推送数据
     */
    private void fetchAndPushData() {
        // 只在交易时间执行
        LocalTime now = LocalTime.now();
        boolean isTradingTime = (now.isAfter(LocalTime.of(9, 25)) && now.isBefore(LocalTime.of(11, 35))) ||
                               (now.isAfter(LocalTime.of(12, 55)) && now.isBefore(LocalTime.of(15, 5)));

        if (!isTradingTime) {
            return;
        }

        if (allSubscribedSymbols.isEmpty()) {
            return;
        }

        try {
            long startTime = System.currentTimeMillis();

            // 批量获取实时数据
            var stockList = tencentStockDataService.getRealtimeDataBatch(
                    allSubscribedSymbols.stream()
                            .map(symbol -> {
                                com.stock.platform.entity.Stock stock = new com.stock.platform.entity.Stock();
                                stock.setSymbol(symbol);
                                return stock;
                            })
                            .collect(Collectors.toList())
            );

            // 更新缓存
            long currentTime = System.currentTimeMillis();
            for (StockDTO stock : stockList) {
                stockCache.put(stock.getSymbol(), stock);
                cacheTimestamp.put(stock.getSymbol(), currentTime);
            }

            long fetchTime = System.currentTimeMillis() - startTime;
            log.debug("Get {} stocks数据耗时 {}ms", stockList.size(), fetchTime);

        } catch (Exception e) {
            log.error("Get实时数据Failed: {}", e.getMessage());
        }
    }

    /**
     * 获取股票数据（优先从缓存）
     */
    public StockDTO getStockData(String symbol) {
        Long timestamp = cacheTimestamp.get(symbol);
        if (timestamp != null && System.currentTimeMillis() - timestamp < CACHE_TTL) {
            return stockCache.get(symbol);
        }
        // 缓存过期，返回旧数据并触发更新
        return stockCache.get(symbol);
    }

    /**
     * 获取客户端订阅的所有股票数据
     */
    public Map<String, StockDTO> getClientStockData(String sessionId) {
        Set<String> symbols = clientSubscriptions.get(sessionId);
        if (symbols == null || symbols.isEmpty()) {
            return Map.of();
        }

        Map<String, StockDTO> result = new ConcurrentHashMap<>();
        for (String symbol : symbols) {
            StockDTO stock = getStockData(symbol);
            if (stock != null) {
                result.put(symbol, stock);
            }
        }
        return result;
    }

    /**
     * 获取客户端订阅列表
     */
    public Set<String> getClientSubscriptions(String sessionId) {
        return clientSubscriptions.getOrDefault(sessionId, ConcurrentHashMap.newKeySet());
    }
}
