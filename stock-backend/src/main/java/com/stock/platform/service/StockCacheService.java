package com.stock.platform.service;

import com.stock.platform.config.RedisConfig;
import com.stock.platform.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 股票数据缓存服务
 * 提供股票相关数据的缓存管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockCacheService {

    private final StockDataService stockDataService;
    private final RedisCacheService redisCacheService;

    /**
     * 获取所有股票（带缓存）
     */
    @Cacheable(value = RedisConfig.CACHE_STOCK_LIST, unless = "#result == null || #result.isEmpty()")
    public List<StockDTO> getAllStocks() {
        log.debug("从数据库Get所有股票");
        return stockDataService.getAllStocks();
    }

    /**
     * 获取大盘指数（带缓存）
     */
    @Cacheable(value = RedisConfig.CACHE_MARKET_INDEX, unless = "#result == null || #result.isEmpty()")
    public List<MarketIndexDTO> getMarketIndices() {
        log.debug("从APIGet大盘指数");
        return stockDataService.getMarketIndices();
    }

    /**
     * 获取股票详情（带缓存）
     */
    @Cacheable(value = RedisConfig.CACHE_STOCK_DETAIL, key = "#symbol", unless = "#result == null")
    public StockDetailDTO getStockDetail(String symbol) {
        log.debug("从数据库Get股票详情: {}", symbol);
        return stockDataService.getStockDetail(symbol);
    }

    /**
     * 获取K线数据（带缓存）
     */
    @Cacheable(value = RedisConfig.CACHE_STOCK_KLINE, key = "#symbol + ':' + #period", unless = "#result == null || #result.isEmpty()")
    public List<KlineDataDTO> getKlineData(String symbol, String period, int limit) {
        log.debug("从数据库GetK线数据: {}, 周期: {}", symbol, period);
        return stockDataService.getKlineData(symbol, period, limit);
    }

    /**
     * 搜索股票（带缓存）
     */
    @Cacheable(value = RedisConfig.CACHE_SEARCH_RESULT, key = "#keyword", unless = "#result == null || #result.isEmpty()")
    public List<StockDTO> searchStocks(String keyword) {
        log.debug("搜索股票: {}", keyword);
        return stockDataService.searchStocks(keyword);
    }

    /**
     * 清除股票列表缓存
     */
    @CacheEvict(value = RedisConfig.CACHE_STOCK_LIST, allEntries = true)
    public void clearStockListCache() {
        log.info("清除股票列表缓存");
    }

    /**
     * 清除大盘指数缓存
     */
    @CacheEvict(value = RedisConfig.CACHE_MARKET_INDEX, allEntries = true)
    public void clearMarketIndexCache() {
        log.info("清除大盘指数缓存");
    }

    /**
     * 清除股票详情缓存
     */
    @CacheEvict(value = RedisConfig.CACHE_STOCK_DETAIL, key = "#symbol")
    public void clearStockDetailCache(String symbol) {
        log.info("清除股票详情缓存: {}", symbol);
    }

    /**
     * 清除K线数据缓存
     */
    @CacheEvict(value = RedisConfig.CACHE_STOCK_KLINE, key = "#symbol + ':' + #period")
    public void clearKlineCache(String symbol, String period) {
        log.info("清除K线缓存: {}, 周期: {}", symbol, period);
    }

    /**
     * 清除所有股票相关缓存
     */
    public void clearAllStockCache() {
        log.info("清除所有股票缓存");
        redisCacheService.deleteByPattern("stock:*");
        redisCacheService.deleteByPattern("market:*");
        redisCacheService.deleteByPattern("search:*");
    }

    /**
     * 更新实时数据时清除相关缓存
     */
    public void refreshRealtimeData() {
        clearStockListCache();
        clearMarketIndexCache();
    }

    // ==================== 实时数据缓存（带过期时间） ====================

    /**
     * 缓存股票实时数据（5分钟过期）
     */
    public void cacheRealtimeData(String symbol, StockDTO data) {
        String key = RedisConfig.CACHE_REALTIME_DATA + ":" + symbol;
        redisCacheService.set(key, data, 5, TimeUnit.MINUTES);
        log.debug("缓存实时数据: {}", symbol);
    }

    /**
     * 获取缓存的实时数据
     */
    public StockDTO getCachedRealtimeData(String symbol) {
        String key = RedisConfig.CACHE_REALTIME_DATA + ":" + symbol;
        StockDTO data = redisCacheService.get(key, StockDTO.class);
        if (data != null) {
            log.debug("从缓存获取实时数据: {}", symbol);
        }
        return data;
    }

    /**
     * 批量缓存实时数据
     */
    public void cacheRealtimeDataBatch(List<StockDTO> stocks) {
        for (StockDTO stock : stocks) {
            if (stock.getSymbol() != null) {
                cacheRealtimeData(stock.getSymbol(), stock);
            }
        }
        log.debug("批量缓存实时数据: {} 条", stocks.size());
    }

    /**
     * 缓存大盘指数（1分钟过期）
     */
    public void cacheMarketIndices(List<MarketIndexDTO> indices) {
        String key = RedisConfig.CACHE_MARKET_INDEX;
        redisCacheService.set(key, indices, 1, TimeUnit.MINUTES);
        log.debug("缓存大盘指数数据");
    }

    /**
     * 获取缓存的大盘指数
     */
    @SuppressWarnings("unchecked")
    public List<MarketIndexDTO> getCachedMarketIndices() {
        String key = RedisConfig.CACHE_MARKET_INDEX;
        return redisCacheService.get(key, List.class);
    }

    /**
     * 清除实时数据缓存
     */
    public void clearRealtimeDataCache(String symbol) {
        String key = RedisConfig.CACHE_REALTIME_DATA + ":" + symbol;
        redisCacheService.delete(key);
        log.debug("清除实时数据缓存: {}", symbol);
    }

    /**
     * 清除所有实时数据缓存
     */
    public void clearAllRealtimeDataCache() {
        redisCacheService.deleteByPattern("stock:realtime:*");
        log.info("清除所有实时数据缓存");
    }
}
