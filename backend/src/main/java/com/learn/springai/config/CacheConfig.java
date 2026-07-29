package com.learn.springai.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    /**
     * A dedicated ObjectMapper for Redis cache serialization.
     *
     * Uses NON_FINAL default typing so that plain List<String>, String, Integer, etc.
     * are stored as simple JSON arrays — NOT as ["java.util.ArrayList",[...]] wrapper
     * tuples that GenericJackson2JsonRedisSerializer uses (which Jackson then tries to
     * treat array[0] as a Java class name).
     */
    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.activateDefaultTypingAsProperty(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                "@class"
        );
        return mapper;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        Jackson2JsonRedisSerializer<Object> serializer =
                new Jackson2JsonRedisSerializer<>(redisObjectMapper(), Object.class);

        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(2))
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(serializer));

        Map<String, RedisCacheConfiguration> customConfigs = new HashMap<>();
        customConfigs.put("countries",     defaultCacheConfig.entryTtl(Duration.ofHours(24)));
        customConfigs.put("visas",         defaultCacheConfig.entryTtl(Duration.ofHours(12)));
        customConfigs.put("visaStats",     defaultCacheConfig.entryTtl(Duration.ofHours(12)));
        customConfigs.put("currencyRates", defaultCacheConfig.entryTtl(Duration.ofHours(6)));
        customConfigs.put("searchResults", defaultCacheConfig.entryTtl(Duration.ofHours(2)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .withInitialCacheConfigurations(customConfigs)
                .build();
    }

    /**
     * Lenient cache error handler: treat any cache GET failure as a cache miss.
     *
     * This transparently handles stale entries written by the old
     * GenericJackson2JsonRedisSerializer (wrapper-array format) which our new
     * property-format serializer cannot deserialize. Instead of throwing a 500,
     * the cache is bypassed and the live data source is called. The response is
     * then stored in the correct format on the next cache PUT, self-healing the
     * cache without any manual Redis flush.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache GET error (treating as miss) — cache='{}' key='{}': {}",
                        cache.getName(), key, e.getMessage());
                // Swallow: Spring will call the real method and re-populate the cache entry
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache PUT error — cache='{}' key='{}': {}",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache EVICT error — cache='{}' key='{}': {}",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache CLEAR error — cache='{}': {}", cache.getName(), e.getMessage());
            }
        };
    }
}
