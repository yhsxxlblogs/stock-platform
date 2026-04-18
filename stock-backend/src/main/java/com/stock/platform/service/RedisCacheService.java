package com.stock.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存服务
 * 提供通用的缓存操作方法
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 设置缓存
     */
    public void set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
            log.debug("Set缓存: key={}", key);
        } catch (Exception e) {
            log.error("Set缓存Failed: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 设置缓存并指定过期时间
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
            log.debug("Set缓存: key={}, timeout={} {}", key, timeout, unit);
        } catch (Exception e) {
            log.error("Set缓存Failed: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 获取缓存
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            // 如果类型匹配直接返回
            if (clazz.isInstance(value)) {
                return (T) value;
            }
            // 使用 ObjectMapper 转换
            return objectMapper.convertValue(value, clazz);
        } catch (Exception e) {
            log.error("Get缓存Failed: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 获取缓存（支持复杂类型）
     */
    public <T> T get(String key, TypeReference<T> typeReference) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            return objectMapper.convertValue(value, typeReference);
        } catch (Exception e) {
            log.error("Get缓存Failed: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 删除缓存
     */
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
            log.debug("Delete缓存: key={}", key);
        } catch (Exception e) {
            log.error("Delete缓存Failed: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 批量删除缓存
     */
    public void delete(Collection<String> keys) {
        try {
            redisTemplate.delete(keys);
            log.debug("批量Delete缓存: count={}", keys.size());
        } catch (Exception e) {
            log.error("批量Delete缓存Failed: error={}", e.getMessage());
        }
    }

    /**
     * 删除匹配模式的缓存
     */
    public void deleteByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Delete匹配缓存: pattern={}, count={}", pattern, keys.size());
            }
        } catch (Exception e) {
            log.error("Delete匹配缓存Failed: pattern={}, error={}", pattern, e.getMessage());
        }
    }

    /**
     * 判断缓存是否存在
     */
    public boolean hasKey(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("检查缓存Failed: key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 设置过期时间
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        try {
            return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
        } catch (Exception e) {
            log.error("Set过期时间Failed: key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 获取过期时间
     */
    public Long getExpire(String key, TimeUnit unit) {
        try {
            return redisTemplate.getExpire(key, unit);
        } catch (Exception e) {
            log.error("Get过期时间Failed: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 递增
     */
    public Long increment(String key, long delta) {
        try {
            return redisTemplate.opsForValue().increment(key, delta);
        } catch (Exception e) {
            log.error("递增Failed: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 递减
     */
    public Long decrement(String key, long delta) {
        try {
            return redisTemplate.opsForValue().decrement(key, delta);
        } catch (Exception e) {
            log.error("递减Failed: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 设置 Hash 缓存
     */
    public void hSet(String key, String hashKey, Object value) {
        try {
            redisTemplate.opsForHash().put(key, hashKey, value);
            log.debug("Set Hash 缓存: key={}, hashKey={}", key, hashKey);
        } catch (Exception e) {
            log.error("Set Hash 缓存Failed: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 获取 Hash 缓存
     */
    @SuppressWarnings("unchecked")
    public <T> T hGet(String key, String hashKey, Class<T> clazz) {
        try {
            Object value = redisTemplate.opsForHash().get(key, hashKey);
            if (value == null) {
                return null;
            }
            if (clazz.isInstance(value)) {
                return (T) value;
            }
            return objectMapper.convertValue(value, clazz);
        } catch (Exception e) {
            log.error("Get Hash 缓存Failed: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 清空所有缓存
     */
    public void clear() {
        try {
            Set<String> keys = redisTemplate.keys("*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("清空所有缓存: count={}", keys.size());
            }
        } catch (Exception e) {
            log.error("清空缓存Failed: error={}", e.getMessage());
        }
    }
}
