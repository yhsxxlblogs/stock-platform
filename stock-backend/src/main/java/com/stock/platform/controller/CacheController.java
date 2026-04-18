package com.stock.platform.controller;

import com.stock.platform.dto.ApiResponse;
import com.stock.platform.service.RedisCacheService;
import com.stock.platform.service.StockCacheService;
import com.stock.platform.service.UserCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 缓存管理控制器
 * 提供缓存查看、清理等管理功能
 */
@Slf4j
@RestController
@RequestMapping("/admin/cache")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CacheController {

    private final RedisCacheService redisCacheService;
    private final StockCacheService stockCacheService;
    private final UserCacheService userCacheService;

    /**
     * 获取缓存统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 这里可以添加更多统计信息
        stats.put("status", "running");
        stats.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * 清除所有股票缓存
     */
    @PostMapping("/clear/stock")
    public ResponseEntity<ApiResponse<String>> clearStockCache() {
        stockCacheService.clearAllStockCache();
        return ResponseEntity.ok(ApiResponse.success("股票缓存已清除"));
    }

    /**
     * 清除所有用户缓存
     */
    @PostMapping("/clear/user")
    public ResponseEntity<ApiResponse<String>> clearUserCache() {
        userCacheService.clearAllUserCache();
        return ResponseEntity.ok(ApiResponse.success("用户缓存已清除"));
    }

    /**
     * 清除所有缓存
     */
    @PostMapping("/clear/all")
    public ResponseEntity<ApiResponse<String>> clearAllCache() {
        redisCacheService.clear();
        return ResponseEntity.ok(ApiResponse.success("所有缓存已清除"));
    }

    /**
     * 清除特定股票缓存
     */
    @PostMapping("/clear/stock/{symbol}")
    public ResponseEntity<ApiResponse<String>> clearStockDetailCache(@PathVariable String symbol) {
        stockCacheService.clearStockDetailCache(symbol);
        return ResponseEntity.ok(ApiResponse.success("股票 " + symbol + " 缓存已清除"));
    }

    /**
     * 刷新实时数据缓存
     */
    @PostMapping("/refresh/realtime")
    public ResponseEntity<ApiResponse<String>> refreshRealtimeCache() {
        stockCacheService.refreshRealtimeData();
        return ResponseEntity.ok(ApiResponse.success("实时数据缓存已刷新"));
    }

    /**
     * 获取缓存键列表（调试用）
     */
    @GetMapping("/keys")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCacheKeys(
            @RequestParam(defaultValue = "*") String pattern) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("pattern", pattern);
        
        // 注意：生产环境慎用，大数据量时性能差
        // 这里仅用于调试
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
