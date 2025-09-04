package kr.hhplus.be.server.domain.auth.kafka

import kr.hhplus.be.server.domain.payment.event.PaymentCompletedEvent
import kr.hhplus.be.server.domain.auth.service.ActiveTokenService
import mu.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.retry.annotation.Backoff
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

@Component
class AuthPaymentEventConsumer(
    private val activeTokenService: ActiveTokenService
) {
    private val logger = KotlinLogging.logger {}
    private val processedCount = AtomicInteger(0)
    
    @RetryableTopic(
        attempts = "3",
        backoff = Backoff(delay = 1000, multiplier = 2.0),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(
        topics = ["payment-events"],
        groupId = "auth-payment-consumer",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handlePaymentEvent(
        @Payload event: Any,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        @Header(name = "kafka_receivedMessageKey", required = false) key: String?
    ) {
        try {
            when (event) {
                is PaymentCompletedEvent -> handleTokenCompletion(event, partition, offset)
                else -> {
                    // Auth 도메인은 결제 완료 이벤트만 관심
                }
            }
            
            val count = processedCount.incrementAndGet()
            logger.debug { "인증 결제 이벤트 처리 완료: count=$count, partition=$partition, offset=$offset" }
            
        } catch (e: Exception) {
            logger.error(e) { "인증 결제 이벤트 처리 실패: partition=$partition, offset=$offset" }
            throw e
        }
    }
    
    private fun handleTokenCompletion(event: PaymentCompletedEvent, partition: Int, offset: Long) {
        logger.info { "결제 완료로 인한 토큰 완료 처리: token=${event.token}, paymentId=${event.paymentId}, partition=$partition, offset=$offset" }
        
        activeTokenService.completeToken(event.token)
        logger.info { "토큰 완료 처리 완료: token=${event.token}" }
    }
    
    fun getProcessedCount(): Int = processedCount.get()
    
    fun resetProcessedCount() {
        processedCount.set(0)
    }
}