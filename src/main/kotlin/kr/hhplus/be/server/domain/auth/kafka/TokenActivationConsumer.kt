package kr.hhplus.be.server.domain.auth.kafka

import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.service.ActiveTokenService
import mu.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

@Component
class TokenActivationConsumer(
    private val activeTokenService: ActiveTokenService
) {
    private val logger = KotlinLogging.logger {}
    private val processedCount = AtomicInteger(0)
    
    @KafkaListener(
        topics = ["waiting-token"],
        groupId = "token-activation-consumer",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun processTokenActivation(
        @Payload waitingToken: WaitingToken,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long
    ) {
        try {
            logger.info { "대기열 토큰 수신: token=${waitingToken.token}, userId=${waitingToken.userId}, partition=$partition, offset=$offset" }
            
            // 토큰 활성화
            activeTokenService.activateToken(waitingToken.token)
            
            val processedCountValue = processedCount.incrementAndGet()
            logger.info { "토큰 활성화 완료: token=${waitingToken.token}, userId=${waitingToken.userId}, processedCount=$processedCountValue" }
            
        } catch (e: Exception) {
            logger.error(e) { "토큰 활성화 처리 실패: token=${waitingToken.token}, userId=${waitingToken.userId}" }
            throw e
        }
    }
    
    fun getProcessedCount(): Int = processedCount.get()
    
    fun resetProcessedCount() {
        processedCount.set(0)
    }
}