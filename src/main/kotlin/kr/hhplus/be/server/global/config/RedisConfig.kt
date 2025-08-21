package kr.hhplus.be.server.global.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.StringRedisTemplate
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
    
    @Value("\${spring.data.redis.host:localhost}")
    private lateinit var redisHost: String

    @Value("\${spring.data.redis.port:6379}")
    private var redisPort: Int = 6379
    
    @Value("\${spring.data.redis.password:}")
    private lateinit var redisPassword: String
    
    @Value("\${spring.data.redis.database:0}")
    private var redisDatabase: Int = 0
    
    @Value("\${spring.data.redis.timeout:2000}")
    private var redisTimeout: Long = 2000
    

    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        val redisConfig = RedisStandaloneConfiguration().apply {
            hostName = redisHost
            port = redisPort
            database = redisDatabase
            if (redisPassword.isNotBlank()) {
                setPassword(redisPassword)
            }
        }
        
        return LettuceConnectionFactory(redisConfig)
    }

    @Bean
    @Qualifier("redis")
    fun redisObjectMapper(): ObjectMapper {
        val polymorphicTypeValidator = BasicPolymorphicTypeValidator.builder()
            .allowIfBaseType(Any::class.java)
            .build()

        return ObjectMapper().apply {
            registerModule(JavaTimeModule())
            registerModule(KotlinModule.Builder().build())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            activateDefaultTyping(polymorphicTypeValidator, ObjectMapper.DefaultTyping.NON_FINAL)
        }
    }

    @Bean
    fun redisSerializer(): GenericJackson2JsonRedisSerializer {
        return GenericJackson2JsonRedisSerializer(redisObjectMapper())
    }

    @Bean
    fun redisTemplate(): RedisTemplate<String, Any> {
        val template = RedisTemplate<String, Any>()
        template.connectionFactory = redisConnectionFactory()
        template.keySerializer = StringRedisSerializer()
        template.valueSerializer = redisSerializer()
        template.hashKeySerializer = StringRedisSerializer()
        template.hashValueSerializer = redisSerializer()
        template.setEnableTransactionSupport(true)
        template.afterPropertiesSet()
        return template
    }

    @Bean
    fun stringRedisTemplate(): StringRedisTemplate {
        val template = StringRedisTemplate()
        template.connectionFactory = redisConnectionFactory()
        template.afterPropertiesSet()
        return template
    }

    @Bean
    fun redisMessageListenerContainer(): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(redisConnectionFactory())
        container.setTaskExecutor(org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 5
            queueCapacity = 100
            setThreadNamePrefix("Redis-Listener-")
            initialize()
        })
        return container
    }

    @Bean
    fun cacheManager(): CacheManager {
        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisSerializer()))
            .disableCachingNullValues()
            .prefixCacheNameWith("concert-service:")

        return RedisCacheManager.builder(redisConnectionFactory())
            .cacheDefaults(defaultConfig)
            .transactionAware()
            
            // 도메인별 캐시 설정
            .withCacheConfiguration("concerts", 
                defaultConfig.entryTtl(Duration.ofHours(2))) // 콘서트 기본 정보
            .withCacheConfiguration("concerts:schedules", 
                defaultConfig.entryTtl(Duration.ofHours(1))) // 스케줄 정보
            .withCacheConfiguration("concerts:detail", 
                defaultConfig.entryTtl(Duration.ofMinutes(30))) // 상세 정보
                
            // 실시간성이 중요한 데이터
            .withCacheConfiguration("concerts:available", 
                defaultConfig.entryTtl(Duration.ofMinutes(5))) // 예약 가능한 콘서트
            .withCacheConfiguration("seats:available", 
                defaultConfig.entryTtl(Duration.ofMinutes(2))) // 예약 가능한 좌석
                
            // 통계 및 랭킹 데이터
            .withCacheConfiguration("concerts:popular", 
                defaultConfig.entryTtl(Duration.ofMinutes(10))) // 인기 콘서트
            .withCacheConfiguration("concerts:trending", 
                defaultConfig.entryTtl(Duration.ofMinutes(3))) // 트렌딩 콘서트
                
            // 시스템 설정 데이터 (잘 변하지 않음)
            .withCacheConfiguration("system:types", 
                defaultConfig.entryTtl(Duration.ofHours(24))) // 상태 타입들
            .withCacheConfiguration("system:config", 
                defaultConfig.entryTtl(Duration.ofHours(12))) // 시스템 설정
                
            // 사용자 관련 (짧은 TTL)
            .withCacheConfiguration("users:balance", 
                defaultConfig.entryTtl(Duration.ofMinutes(5))) // 사용자 잔액
            .withCacheConfiguration("tokens:status", 
                defaultConfig.entryTtl(Duration.ofMinutes(1))) // 토큰 상태
            
            .build()
    }
}
