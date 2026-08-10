package com.dididi.booking.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;

/**
 * Bat caching va dung Redis lam cache store (tan dung Redis san co cho session/refresh token).
 * Gia tri cache serialize bang JDK (cac DTO implements Serializable) - don gian & chac chan voi
 * record + kieu java.time, tranh rac roi @class cua JSON. TTL o app.cache.ttl-minutes (mac dinh 10).
 *
 * CacheErrorHandler: neu 1 entry cache cu KHONG doc/ghi duoc (vd doi cau truc DTO -> JDK
 * deserialize loi InvalidClassException), thi BO QUA cache + tinh lai + ghi de, thay vi nem 500.
 * Tranh "cache poisoning" lam hong API doc (vd GET /hotels/{id}).
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                          ResourceLoader resourceLoader,
                                          @Value("${app.cache.ttl-minutes:10}") long ttlMinutes) {
        // QUAN TRỌNG: truyền ClassLoader hiện tại cho bộ giải mã JDK.
        // defaultCacheConfig() KHÔNG tham số dùng classloader mặc định của ứng dụng; khi chạy dev
        // với spring-boot-devtools, code đang chạy lại nằm ở RestartClassLoader -> object đọc từ
        // Redis là "cùng tên lớp nhưng khác classloader" => ClassCastException kiểu
        // "FlightApiDto cannot be cast to FlightApiDto" NGAY KHI code chạm vào field của DTO cache
        // (lọc/sắp xếp). Trước đây controller chỉ trả thẳng list nên Jackson đọc bằng reflection và
        // lỗi này bị GIẤU. resourceLoader.getClassLoader() = đúng loader đang chạy sau mỗi lần restart.
        RedisCacheConfiguration config = RedisCacheConfiguration
                .defaultCacheConfig(resourceLoader.getClassLoader())
                .entryTtl(Duration.ofMinutes(ttlMinutes))
                .disableCachingNullValues();
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                // Entry hong/cu -> xoa + coi nhu cache miss (Spring se tinh lai va ghi de).
                log.warn("Cache GET loi {}::{} -> bo qua, tinh lai. {}", cache.getName(), key, e.toString());
                try {
                    cache.evict(key);
                } catch (RuntimeException ignore) {
                    // ignore
                }
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache PUT loi {}::{} -> bo qua. {}", cache.getName(), key, e.toString());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache EVICT loi {}::{} -> bo qua. {}", cache.getName(), key, e.toString());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache CLEAR loi {} -> bo qua. {}", cache.getName(), e.toString());
            }
        };
    }
}
