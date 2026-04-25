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

    // ==================== K线数据缓存（历史数据，24小时过期） ====================

    /**
     * 缓存K线数据（24小时过期）
     * 历史数据高度一致，仅当日线有差异时需要更新
     */
    public void cacheKlineData(String symbol, String period, List<KlineDataDTO> data) {
        String key = RedisConfig.CACHE_STOCK_KLINE + ":" + symbol + ":" + period;
        log.info("准备缓存K线数据: key={}, 条数={}", key, data.size());
        try {
            redisCacheService.set(key, data, 24, TimeUnit.HOURS);
            // 验证是否写入成功
            List<KlineDataDTO> cached = getCachedKlineData(symbol, period);
            if (cached != null && !cached.isEmpty()) {
                log.info("K线数据缓存成功: key={}, 验证条数={}", key, cached.size());
            } else {
                log.error("K线数据缓存失败: key={}, 写入后读取为空", key);
            }
        } catch (Exception e) {
            log.error("K线数据缓存异常: key={}, 错误={}", key, e.getMessage(), e);
        }
    }

    /**
     * 获取缓存的K线数据
     */
    public List<KlineDataDTO> getCachedKlineData(String symbol, String period) {
        String key = RedisConfig.CACHE_STOCK_KLINE + ":" + symbol + ":" + period;
        log.info("尝试从缓存获取K线数据: key={}", key);
        try {
            // 使用 TypeReference 来保留泛型类型信息
            com.fasterxml.jackson.core.type.TypeReference<List<KlineDataDTO>> typeRef = 
                new com.fasterxml.jackson.core.type.TypeReference<List<KlineDataDTO>>() {};
            List<KlineDataDTO> data = redisCacheService.get(key, typeRef);
            if (data != null && !data.isEmpty()) {
                log.info("从缓存获取K线数据成功: {}, 周期: {}, 条数: {}", symbol, period, data.size());
                return data;
            } else {
                log.info("缓存未命中或为空: {}, 周期: {}", symbol, period);
            }
        } catch (Exception e) {
            log.error("从缓存获取K线数据失败: {}, 周期: {}, 错误: {}", symbol, period, e.getMessage());
        }
        return null;
    }

    /**
     * 清除K线数据缓存
     * 每日收盘后调用，清除当日缓存以便次日获取最新数据
     */
    public void clearKlineDataCache(String symbol, String period) {
        String key = RedisConfig.CACHE_STOCK_KLINE + ":" + symbol + ":" + period;
        redisCacheService.delete(key);
        log.debug("清除K线数据缓存: {}, 周期: {}", symbol, period);
    }

    /**
     * 清除所有K线数据缓存
     * 每日收盘后调用
     */
    public void clearAllKlineDataCache() {
        redisCacheService.deleteByPattern("stock:kline:*");
        log.info("清除所有K线数据缓存");
    }
}
