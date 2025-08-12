package kr.hhplus.be.server.global.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
@EnableCaching
@EnableRedisRepositories
class RedisConfig {
    @Value("\${spring.data.redis.host}")
    private lateinit var redisHost: String

    @Value("\${spring.data.redis.port}")
    private var redisPort: Int = 0

    @Bean
    fun redisConnectionFactory(): LettuceConnectionFactory {
        return LettuceConnectionFactory(
            RedisStandaloneConfiguration(redisHost, redisPort)
        )
    }

    @Bean
    fun redisTemplate(): RedisTemplate<String, Any> {
        val template = RedisTemplate<String, Any>()
        template.connectionFactory = redisConnectionFactory()
        template.keySerializer = StringRedisSerializer()
        template.valueSerializer = GenericJackson2JsonRedisSerializer()
        template.hashKeySerializer = StringRedisSerializer()
        template.hashValueSerializer = GenericJackson2JsonRedisSerializer()
        return template
    }

    @Bean
    fun cacheManager(): CacheManager {
        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues()

        return RedisCacheManager.builder(redisConnectionFactory())
            .cacheDefaults(defaultConfig)
            .withCacheConfiguration("seats:available", 
                defaultConfig.entryTtl(Duration.ofMinutes(5)))
            .withCacheConfiguration("seats:all", 
                defaultConfig.entryTtl(Duration.ofMinutes(10)))
            .withCacheConfiguration("seats:layout", 
                defaultConfig.entryTtl(Duration.ofHours(2)))
            .withCacheConfiguration("schedules", 
                defaultConfig.entryTtl(Duration.ofHours(1)))
            .withCacheConfiguration("concerts", 
                defaultConfig.entryTtl(Duration.ofHours(2)))
            .withCacheConfiguration("concerts:available", 
                defaultConfig.entryTtl(Duration.ofMinutes(30)))
            .withCacheConfiguration("concerts:detail", 
                defaultConfig.entryTtl(Duration.ofHours(1)))
            .withCacheConfiguration("status:types", 
                defaultConfig.entryTtl(Duration.ofHours(24)))
            .build()
    }
}