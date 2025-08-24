package kr.hhplus.be.server.global.config

import kr.hhplus.be.server.global.constants.CacheConstants
import kr.hhplus.be.server.global.properties.CacheProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration

@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties::class)
class CacheConfig {

    @Bean
    fun cacheManager(
        redisConnectionFactory: RedisConnectionFactory,
        cacheProperties: CacheProperties,
        @Qualifier("redis") objectMapper: ObjectMapper
    ): RedisCacheManager {
        val defaultConfig = createDefaultCacheConfiguration(cacheProperties, objectMapper)
        
        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(defaultConfig)
            .transactionAware()
            
            // ========== 상태 타입 캐시 (Repository와 일치) ==========
            .withCacheConfiguration(CacheConstants.SEAT_STATUS_TYPES,
                defaultConfig.entryTtl(CacheConstants.STATUS_TYPE_TTL))
            
            .withCacheConfiguration(CacheConstants.RESERVATION_STATUS_TYPES,
                defaultConfig.entryTtl(CacheConstants.STATUS_TYPE_TTL))
            
            .withCacheConfiguration(CacheConstants.PAYMENT_STATUS_TYPES,
                defaultConfig.entryTtl(CacheConstants.STATUS_TYPE_TTL))
            
            .withCacheConfiguration(CacheConstants.POINT_HISTORY_TYPES,
                defaultConfig.entryTtl(CacheConstants.STATUS_TYPE_TTL))
            
            // ========== 콘서트 관련 캐시 ==========
            .withCacheConfiguration(CacheConstants.CONCERTS,
                defaultConfig.entryTtl(CacheConstants.CONCERT_TTL))
            
            .withCacheConfiguration(CacheConstants.CONCERT_SCHEDULES,
                defaultConfig.entryTtl(CacheConstants.CONCERT_SCHEDULE_TTL))
            
            .withCacheConfiguration(CacheConstants.CONCERT_DETAILS,
                defaultConfig.entryTtl(CacheConstants.CONCERT_DETAIL_TTL))
            
            .withCacheConfiguration(CacheConstants.AVAILABLE_CONCERTS,
                defaultConfig.entryTtl(CacheConstants.AVAILABLE_CONCERT_TTL))
            
            .withCacheConfiguration(CacheConstants.POPULAR_CONCERTS,
                defaultConfig.entryTtl(CacheConstants.POPULAR_CONCERT_TTL))
            
            .withCacheConfiguration(CacheConstants.TRENDING_CONCERTS,
                defaultConfig.entryTtl(CacheConstants.TRENDING_CONCERT_TTL))
            
            // ========== 좌석 관련 캐시 ==========
            .withCacheConfiguration(CacheConstants.AVAILABLE_SEATS,
                defaultConfig.entryTtl(CacheConstants.SEAT_TTL))
            
            // ========== 사용자 관련 캐시 ==========
            .withCacheConfiguration(CacheConstants.USER_BALANCE,
                defaultConfig.entryTtl(CacheConstants.USER_BALANCE_TTL))
            
            .withCacheConfiguration(CacheConstants.TOKEN_STATUS,
                defaultConfig.entryTtl(CacheConstants.TOKEN_TTL))
            
            .build()
    }
    
    private fun createDefaultCacheConfiguration(
        cacheProperties: CacheProperties,
        objectMapper: ObjectMapper
    ): RedisCacheConfiguration {
        // Use the same ObjectMapper that has JsonTypeInfo configuration
        val jsonSerializer = GenericJackson2JsonRedisSerializer(objectMapper)
        
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(cacheProperties.defaultTtl)
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
            .disableCachingNullValues()
            .computePrefixWith { cacheName -> "$cacheName:" }
    }
}