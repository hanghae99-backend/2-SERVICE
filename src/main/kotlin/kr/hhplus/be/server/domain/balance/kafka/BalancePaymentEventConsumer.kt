package kr.hhplus.be.server.domain.balance.kafka

import kr.hhplus.be.server.domain.payment.event.PaymentFailedEvent
import kr.hhplus.be.server.domain.balance.service.BalanceService
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
class BalancePaymentEventConsumer(
    private val balanceService: BalanceService
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
        groupId = "balance-payment-consumer",
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
                is PaymentFailedEvent -> handleBalanceRestore(event, partition, offset)
                else -> {
                    // Balance 도메인은 결제 실패 이벤트만 관심
                }
            }
            
            val count = processedCount.incrementAndGet()
            logger.debug { "잔고 결제 이벤트 처리 완료: count=$count, partition=$partition, offset=$offset" }
            
        } catch (e: Exception) {
            logger.error(e) { "잔고 결제 이벤트 처리 실패: partition=$partition, offset=$offset" }
            throw e
        }
    }
    
    private fun handleBalanceRestore(event: PaymentFailedEvent, partition: Int, offset: Long) {
        logger.info { "결제 실패로 인한 잔고 복구: userId=${event.userId}, amount=${event.amount}, partition=$partition, offset=$offset" }
        
        if (event.needsBalanceRestore && event.amount != null) {
            balanceService.restoreBalance(event.userId, event.amount, "결제 실패로 인한 잔고 복구")
            logger.info { "잔고 복구 완료: userId=${event.userId}, amount=${event.amount}" }
        } else {
            logger.debug { "잔고 복구 불필요: userId=${event.userId}, needsRestore=${event.needsBalanceRestore}" }
        }
    }
    
    fun getProcessedCount(): Int = processedCount.get()
    
    fun resetProcessedCount() {
        processedCount.set(0)
    }
}