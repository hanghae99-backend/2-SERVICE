package kr.hhplus.be.server.global.config

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka

@Configuration
@EnableKafka
class KafkaConfig {
    
    @Bean
    fun waitingTokenTopic(): NewTopic {
        return NewTopic("waiting-token", 1, 1)
            .configs(mapOf(
                "retention.ms" to "86400000",
                "cleanup.policy" to "delete"
            ))
    }
    
    @Bean
    fun reservationEventsTopic(): NewTopic {
        return NewTopic("reservation-events", 3, 1)
            .configs(mapOf(
                "retention.ms" to "604800000",  // 7일
                "cleanup.policy" to "delete"
            ))
    }
}