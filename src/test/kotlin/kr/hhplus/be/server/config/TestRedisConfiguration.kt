package kr.hhplus.be.server.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import jakarta.annotation.PreDestroy

/**
 * 통합/동시성 테스트용 Redis 설정
 * TestContainers로 실제 Redis 사용, 실패시 예외 발생
 */
@TestConfiguration
class TestRedisConfiguration {
    
    private var redisContainer: Any? = null
    
    init {
        try {
            // TestContainers Redis 컨테이너 시작
            val containerClass = Class.forName("org.testcontainers.containers.GenericContainer")
            val dockerImageClass = Class.forName("org.testcontainers.utility.DockerImageName")
            
            val dockerImage = dockerImageClass.getMethod("parse", String::class.java)
                .invoke(null, "redis:7-alpine")
            
            redisContainer = containerClass.getConstructor(dockerImageClass)
                .newInstance(dockerImage)
            
            containerClass.getMethod("withExposedPorts", Array<Int>::class.java)
                .invoke(redisContainer, arrayOf(6379))
            containerClass.getMethod("start").invoke(redisContainer)
            
            println("✅ Redis container started for integration tests")
            
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to start Redis container for integration tests. " +
                "Please ensure Docker is running or use unit tests with mocks instead.", e
            )
        }
    }
    
    @Bean
    @Primary
    fun testRedisConnectionFactory(): RedisConnectionFactory {
        val containerClass = redisContainer!!::class.java
        val host = containerClass.getMethod("getHost").invoke(redisContainer) as String
        val port = containerClass.getMethod("getMappedPort", Int::class.java)
            .invoke(redisContainer, 6379) as Int
            
        val factory = LettuceConnectionFactory(host, port)
        factory.afterPropertiesSet()
        return factory
    }
    
    @Bean
    @Primary
    fun testStringRedisTemplate(connectionFactory: RedisConnectionFactory): StringRedisTemplate {
        val template = StringRedisTemplate()
        template.connectionFactory = connectionFactory
        template.afterPropertiesSet()
        return template
    }
    
    @PreDestroy
    fun cleanup() {
        try {
            redisContainer?.let { container ->
                val containerClass = container::class.java
                containerClass.getMethod("stop").invoke(container)
                println("✅ Redis container stopped")
            }
        } catch (e: Exception) {
            println("⚠️ Failed to stop Redis container: ${e.message}")
        }
    }
}
