package com.mkwang.backend.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
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

/**
 * Redis configuration — dual-purpose:
 * <ol>
 * <li><b>Manual cache</b>: {@code RedisTemplate<String,String>} — used by
 * {@code JwtService}
 * for token version cache, stored as plain String (fast, debuggable).</li>
 * <li><b>Spring Cache abstraction</b>: {@code RedisCacheManager} — supports
 * {@code @Cacheable},
 * {@code @CacheEvict}, {@code @CachePut} annotations with JSON
 * serialization.</li>
 * </ol>
 *
 * <p>
 * WHY GenericJackson2JsonRedisSerializer for Cache Manager?
 * <ul>
 * <li>Stores values as human-readable JSON (debuggable in RedisInsight).</li>
 * <li>Embeds type metadata {@code @class} into JSON — allows safe
 * deserialization back to
 * original DTO/Entity type without explicit type casting.</li>
 * <li>Supports Java 8 date/time (LocalDateTime) via
 * {@code JavaTimeModule}.</li>
 * <li>No classpath coupling issues of JDK serializer.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class RedisConfig {

        /** Default TTL for @Cacheable entries, injected from application.yml */
        @Value("${spring.cache.redis.time-to-live:3600000}")
        private long defaultCacheTtlMs;

        /** TTL riêng cho system_configs — config thay đổi ít, cache 24h giảm DB roundtrip */
        @Value("${app.cache.system-config-ttl-ms:86400000}")
        private long systemConfigCacheTtlMs;

        /**
         * RedisTemplate<String, Object> for manual object caching (e.g., OTP DTOs, token versions).
         * Values are serialized as JSON with type metadata.
         */
        @Bean
        public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
                RedisTemplate<String, Object> template = new RedisTemplate<>();
                template.setConnectionFactory(connectionFactory);

                StringRedisSerializer keySerializer = new StringRedisSerializer();
                GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(buildCacheObjectMapper());

                template.setKeySerializer(keySerializer);
                template.setValueSerializer(valueSerializer);
                template.setHashKeySerializer(keySerializer);
                template.setHashValueSerializer(valueSerializer);

                template.afterPropertiesSet();
                return template;
        }

        // ── Spring Cache Abstraction: RedisCacheManager ─────────────────────────
        // Used by @Cacheable, @CacheEvict, @CachePut — stores objects as JSON with type
        // metadata.

        /**
         * Custom ObjectMapper for Cache serialization ONLY — NOT exposed as Spring Bean
         * to prevent Spring MVC from using it as the global Jackson mapper.
         * - JavaTimeModule: support LocalDate, LocalDateTime, ZonedDateTime
         * - WRITE_DATES_AS_TIMESTAMPS: false → ISO-8601 format in Redis
         * - Default typing NON_FINAL: embed "@class" for safe deserialization from
         * Redis
         */
        private ObjectMapper buildCacheObjectMapper() {
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new JavaTimeModule());
                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                mapper.activateDefaultTyping(
                                LaissezFaireSubTypeValidator.instance,
                                ObjectMapper.DefaultTyping.NON_FINAL,
                                JsonTypeInfo.As.PROPERTY);
                return mapper;
        }

        /**
         * RedisCacheManager — backing store for Spring Cache annotations.
         * <ul>
         * <li>Default TTL: configured via {@code spring.cache.redis.time-to-live}
         * (ms).</li>
         * <li>Key serializer: StringRedisSerializer → human-readable keys.</li>
         * <li>Value serializer: GenericJackson2JsonRedisSerializer with custom
         * ObjectMapper.</li>
         * <li>Null values are NOT cached → prevents stale null pollution.</li>
         * <li>Key prefix enabled → namespaces keys by cache name (e.g.
         * "users::1").</li>
         * </ul>
         */
        @Bean
        public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
                GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(
                                buildCacheObjectMapper());

                RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMillis(defaultCacheTtlMs)) // Default TTL from config
                                .disableCachingNullValues() // Don't cache null results
                                .prefixCacheNameWith("") // Use Spring default "cacheName::" prefix
                                .serializeKeysWith(
                                                RedisSerializationContext.SerializationPair
                                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(
                                                RedisSerializationContext.SerializationPair
                                                                .fromSerializer(valueSerializer));

                return RedisCacheManager.builder(connectionFactory)
                                .cacheDefaults(cacheConfig)
                                // system_configs: TTL 24h — data thay đổi ít, dùng evict() khi update
                                .withCacheConfiguration(
                                        "system_configs",
                                        cacheConfig.entryTtl(Duration.ofMillis(systemConfigCacheTtlMs)))
                                .transactionAware() // Sync cache ops with DB @Transactional boundaries
                                .build();
        }
}
