package com.stock.platform.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 配置类
 * 配置缓存管理器和 RedisTemplate
 */
@Slf4j
@Configuration
@EnableCaching
public class RedisConfig extends CachingConfigurerSupport {

    /**
     * 缓存名称常量
     */
    public static final String CACHE_STOCK_REALTIME = "stock:realtime";
    public static final String CACHE_STOCK_DETAIL = "stock:detail";
    public static final String CACHE_STOCK_LIST = "stock:list";
    public static final String CACHE_MARKET_INDEX = "market:index";
    public static final String CACHE_USER_INFO = "user:info";
    public static final String CACHE_USER_FAVORITES = "user:favorites";
    public static final String CACHE_STOCK_KLINE = "stock:kline";
    public static final String CACHE_SEARCH_RESULT = "search:result";

    /**
     * 缓存过期时间（分钟）
     */
    private static final long DEFAULT_EXPIRE = 30;
    private static final long STOCK_REALTIME_EXPIRE = 1;
    private static final long STOCK_DETAIL_EXPIRE = 60;
    private static final long MARKET_INDEX_EXPIRE = 5;
    private static final long USER_INFO_EXPIRE = 120;
    private static final long KLINE_EXPIRE = 1440;

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 使用 String 序列化器作为 key 的序列化器
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // 使用 JSON 序列化器作为 value 的序列化器
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        log.info("RedisTemplate configured");
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 默认缓存配置
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(DEFAULT_EXPIRE))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // 自定义不同缓存的过期时间
        Map<String, RedisCacheConfiguration> configMap = new HashMap<>();
        configMap.put(CACHE_STOCK_REALTIME, defaultConfig.entryTtl(Duration.ofMinutes(STOCK_REALTIME_EXPIRE)));
        configMap.put(CACHE_STOCK_DETAIL, defaultConfig.entryTtl(Duration.ofMinutes(STOCK_DETAIL_EXPIRE)));
        configMap.put(CACHE_MARKET_INDEX, defaultConfig.entryTtl(Duration.ofMinutes(MARKET_INDEX_EXPIRE)));
        configMap.put(CACHE_USER_INFO, defaultConfig.entryTtl(Duration.ofMinutes(USER_INFO_EXPIRE)));
        configMap.put(CACHE_STOCK_KLINE, defaultConfig.entryTtl(Duration.ofMinutes(KLINE_EXPIRE)));

        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configMap)
                .transactionAware()
                .build();

        log.info("CacheManager configured，Default expiration time: {} minutes", DEFAULT_EXPIRE);
        return cacheManager;
    }

    @Bean
    @Override
    public KeyGenerator keyGenerator() {
        return (target, method, params) -> {
            StringBuilder sb = new StringBuilder();
            sb.append(target.getClass().getSimpleName()).append(":");
            sb.append(method.getName()).append(":");
            for (Object param : params) {
                if (param != null) {
                    sb.append(param.toString()).append("-");
                }
            }
            return sb.toString();
        };
    }
}
