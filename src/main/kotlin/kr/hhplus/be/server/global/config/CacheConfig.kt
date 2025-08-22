package kr.hhplus.be.server.global.config

import kr.hhplus.be.server.global.properties.CacheProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer

@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties::class)
class CacheConfig {

    @Bean
    fun cacheManager(
        redisConnectionFactory: RedisConnectionFactory,
        cacheProperties: CacheProperties,
        redisSerializer: GenericJackson2JsonRedisSerializer
    ): RedisCacheManager {
        val defaultConfig = createDefaultCacheConfiguration(cacheProperties, redisSerializer)
        
        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(defaultConfig)
            .transactionAware()
            
            .withCacheConfiguration("concerts", 
                defaultConfig.entryTtl(cacheProperties.concerts.ttl)
                    .prefixCacheNameWith("${cacheProperties.keyPrefix}concerts:"))
                    
            .withCacheConfiguration("concerts:schedules", 
                defaultConfig.entryTtl(cacheProperties.concertSchedules.ttl)
                    .prefixCacheNameWith("${cacheProperties.keyPrefix}schedules:"))
                    
            .withCacheConfiguration("concerts:detail", 
                defaultConfig.entryTtl(cacheProperties.concertDetails.ttl)
                    .prefixCacheNameWith("${cacheProperties.keyPrefix}details:"))
                    
            .withCacheConfiguration("concerts:available", 
                defaultConfig.entryTtl(cacheProperties.availableConcerts.ttl)
                    .prefixCacheNameWith("${cacheProperties.keyPrefix}available:"))
                    
            .withCacheConfiguration("seats:available", 
                defaultConfig.entryTtl(cacheProperties.availableSeats.ttl)
                    .prefixCacheNameWith("${cacheProperties.keyPrefix}seats:"))
                    
            .withCacheConfiguration("concerts:popular", 
                defaultConfig.entryTtl(cacheProperties.popularConcerts.ttl)
                    .prefixCacheNameWith("${cacheProperties.keyPrefix}popular:"))
                    
            .withCacheConfiguration("concerts:trending", 
                defaultConfig.entryTtl(cacheProperties.trendingConcerts.ttl)
                    .prefixCacheNameWith("${cacheProperties.keyPrefix}trending:"))
                    
            .withCacheConfiguration("system:types", 
                defaultConfig.entryTtl(cacheProperties.systemTypes.ttl)
                    .prefixCacheNameWith("${cacheProperties.keyPrefix}types:"))
                    
            .withCacheConfiguration("system:config", 
                defaultConfig.entryTtl(cacheProperties.systemConfig.ttl)
                    .prefixCacheNameWith("${cacheProperties.keyPrefix}config:"))
                    
            .withCacheConfiguration("users:balance", 
                defaultConfig.entryTtl(cacheProperties.userBalance.ttl)
                    .prefixCacheNameWith("${cacheProperties.keyPrefix}balance:"))
                    
            .withCacheConfiguration("tokens:status", 
                defaultConfig.entryTtl(cacheProperties.tokenStatus.ttl)
                    .prefixCacheNameWith("${cacheProperties.keyPrefix}tokens:"))
            
            .build()
    }
    
    private fun createDefaultCacheConfiguration(
        cacheProperties: CacheProperties,
        redisSerializer: GenericJackson2JsonRedisSerializer
    ): RedisCacheConfiguration {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(cacheProperties.defaultTtl)
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisSerializer))
            .disableCachingNullValues()
            .computePrefixWith { cacheName -> "${cacheProperties.keyPrefix}$cacheName:" }
    }
}