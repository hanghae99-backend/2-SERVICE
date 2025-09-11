package kr.hhplus.be.server.global.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
@EnableRedisRepositories
class RedisConfig {

    @Bean
    @Qualifier("redis")
    fun redisObjectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            registerModule(JavaTimeModule())
            registerModule(KotlinModule.Builder().build())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            
            // Enable type information for domain classes to prevent LinkedHashMap deserialization issues
            val typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubTypeIsArray()
                .allowIfBaseType("kr.hhplus.be.server.domain")
                .allowIfBaseType("kr.hhplus.be.server.api")
                .allowIfSubTypeIsArray()
                .allowIfSubType("kr.hhplus.be.server.api.concert.dto.ConcertScheduleWithInfoDto")
                .allowIfSubType("kr.hhplus.be.server.api.concert.dto.ConcertDto")
                .allowIfSubType("kr.hhplus.be.server.api.concert.dto.ConcertDetailDto")
                .allowIfSubType("kr.hhplus.be.server.api.concert.dto.ConcertWithScheduleDto")
                .allowIfSubType("kr.hhplus.be.server.api.concert.dto.SeatDto")
                .allowIfSubType("kr.hhplus.be.server.api.concert.dto.PopularConcertDto")
                .allowIfSubType("kr.hhplus.be.server.domain.reservation.dto.SelloutRankingDto")
                .allowIfSubType("java.util.ArrayList")
                .allowIfSubType("java.util.LinkedHashMap") 
                .allowIfSubType("java.util.HashMap")
                .allowIfSubType("java.util.List")
                .allowIfSubType("java.util.Map")
                .allowIfSubType("java.lang.String")
                .allowIfSubType("java.lang.Number")
                .allowIfSubType("java.time.LocalDate")
                .allowIfSubType("java.time.LocalDateTime")
                .build()
            
            activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.EVERYTHING)
        }
    }

    @Bean
    @Primary
    fun redisTemplate(
        redisConnectionFactory: RedisConnectionFactory,
        @Qualifier("redis") objectMapper: ObjectMapper
    ): RedisTemplate<String, Any> {
        val template = RedisTemplate<String, Any>()
        template.connectionFactory = redisConnectionFactory
        template.keySerializer = StringRedisSerializer()
        template.valueSerializer = GenericJackson2JsonRedisSerializer(objectMapper)
        template.hashKeySerializer = StringRedisSerializer()
        template.hashValueSerializer = GenericJackson2JsonRedisSerializer(objectMapper)
        template.afterPropertiesSet()
        return template
    }
}
