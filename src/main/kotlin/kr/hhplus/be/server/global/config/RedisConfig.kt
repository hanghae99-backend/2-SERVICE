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
import org.springframework.data.redis.listener.RedisMessageListenerContainer
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
    fun redisMessageListenerContainer(): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(redisConnectionFactory())
        return container
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
            
            // 기본 캐시들 - 안정적인 데이터
            .withCacheConfiguration("concerts", 
                defaultConfig.entryTtl(Duration.ofHours(2))) // 콘서트 정보는 오래 유지
            .withCacheConfiguration("schedules", 
                defaultConfig.entryTtl(Duration.ofHours(1)))
            .withCacheConfiguration("concerts:detail", 
                defaultConfig.entryTtl(Duration.ofMinutes(30)))
                
            // 예약 관련 - 자주 변하는 데이터
            .withCacheConfiguration("concerts:available", 
                defaultConfig.entryTtl(Duration.ofMinutes(5))) // 5분으로 단축
            
            // 인기/트렌딩 - 실시간성 중요
            .withCacheConfiguration("concerts:popular:main", 
                defaultConfig.entryTtl(Duration.ofMinutes(2))) // 2분으로 단축
            .withCacheConfiguration("concerts:trending", 
                defaultConfig.entryTtl(Duration.ofMinutes(1))) // 1분으로 단축
                
            // 시스템 설정 - 길게 유지
            .withCacheConfiguration("status:types", 
                defaultConfig.entryTtl(Duration.ofHours(24)))
            .build()
    }
}