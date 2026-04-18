package com.stock.platform.service;

import com.stock.platform.config.RedisConfig;
import com.stock.platform.dto.UserDTO;
import com.stock.platform.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * User Cache Service
 * Provides user-related data cache management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCacheService {

    private final RedisCacheService redisCacheService;

    /**
     * Cache user info
     */
    @CachePut(value = RedisConfig.CACHE_USER_INFO, key = "#user.id")
    public UserDTO cacheUserInfo(UserDTO user) {
        log.debug("Cache user info: {}", user.getId());
        return user;
    }

    /**
     * Get cached user info
     */
    @Cacheable(value = RedisConfig.CACHE_USER_INFO, key = "#userId", unless = "#result == null")
    public UserDTO getCachedUserInfo(Long userId) {
        log.debug("User info not cached: {}", userId);
        return null;
    }

    /**
     * Clear user cache
     */
    @CacheEvict(value = RedisConfig.CACHE_USER_INFO, key = "#userId")
    public void clearUserCache(Long userId) {
        log.info("Clear user cache: {}", userId);
    }

    /**
     * Cache user favorites
     */
    public void cacheUserFavorites(Long userId, List<String> stockSymbols) {
        String key = RedisConfig.CACHE_USER_FAVORITES + ":" + userId;
        redisCacheService.set(key, stockSymbols, 30, java.util.concurrent.TimeUnit.MINUTES);
        log.debug("Cache user favorites: {}, count: {}", userId, stockSymbols.size());
    }

    /**
     * Get cached user favorites
     */
    @SuppressWarnings("unchecked")
    public List<String> getCachedFavorites(Long userId) {
        String key = RedisConfig.CACHE_USER_FAVORITES + ":" + userId;
        return (List<String>) redisCacheService.get(key, List.class);
    }

    /**
     * Clear user favorites cache
     */
    public void clearFavoritesCache(Long userId) {
        String key = RedisConfig.CACHE_USER_FAVORITES + ":" + userId;
        redisCacheService.delete(key);
        log.info("Clear user favorites cache: {}", userId);
    }

    /**
     * Record user login status (for single sign-on control)
     */
    public void recordUserLogin(Long userId, String token) {
        String key = "user:login:" + userId;
        redisCacheService.set(key, token, 24, java.util.concurrent.TimeUnit.HOURS);
        log.debug("Record user login status: {}", userId);
    }

    /**
     * Get user current login token
     */
    public String getUserLoginToken(Long userId) {
        String key = "user:login:" + userId;
        return redisCacheService.get(key, String.class);
    }

    /**
     * Check if user is logged in
     */
    public boolean isUserLoggedIn(Long userId) {
        String key = "user:login:" + userId;
        return redisCacheService.hasKey(key);
    }

    /**
     * User logout
     */
    public void userLogout(Long userId) {
        String key = "user:login:" + userId;
        redisCacheService.delete(key);
        clearUserCache(userId);
        log.info("User logout: {}", userId);
    }

    /**
     * Record login failure count (prevent brute force)
     */
    public void recordLoginFailure(String username) {
        String key = "login:fail:" + username;
        Long count = redisCacheService.increment(key, 1);
        if (count != null && count == 1) {
            // First failure, set 5 minute expiration
            redisCacheService.expire(key, 5, java.util.concurrent.TimeUnit.MINUTES);
        }
        log.debug("Record login failure: {}, count: {}", username, count);
    }

    /**
     * Get login failure count
     */
    public Long getLoginFailureCount(String username) {
        String key = "login:fail:" + username;
        Long count = redisCacheService.get(key, Long.class);
        return count != null ? count : 0;
    }

    /**
     * Clear login failure record
     */
    public void clearLoginFailure(String username) {
        String key = "login:fail:" + username;
        redisCacheService.delete(key);
        log.debug("Clear login failure record: {}", username);
    }

    /**
     * Check if login is locked
     */
    public boolean isLoginLocked(String username) {
        Long count = getLoginFailureCount(username);
        return count >= 5; // Lock after 5 failures
    }

    /**
     * Clear all user cache
     */
    public void clearAllUserCache() {
        log.info("Clear all user cache");
        redisCacheService.deleteByPattern("user:*");
        redisCacheService.deleteByPattern("login:*");
    }
}
