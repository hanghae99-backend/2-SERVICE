package kr.hhplus.be.server.domain.payment.kafka

import kr.hhplus.be.server.domain.payment.event.PaymentCompletedEvent
import kr.hhplus.be.server.domain.payment.event.PaymentFailedEvent
import kr.hhplus.be.server.global.client.ConcertDataPlatformClient
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
class PaymentEventConsumer(
    private val concertDataPlatformClient: ConcertDataPlatformClient
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
        groupId = "payment-consumer",
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
                is PaymentCompletedEvent -> handlePaymentCompleted(event, partition, offset)
                is PaymentFailedEvent -> handlePaymentFailed(event, partition, offset)
                else -> logger.warn { "알 수 없는 결제 이벤트 타입: ${event::class.simpleName}" }
            }
            
            processedCount.incrementAndGet()
            
        } catch (e: Exception) {
            logger.error(e) { "결제 이벤트 처리 실패: partition=$partition, offset=$offset" }
            throw e
        }
    }
    
    private fun handlePaymentCompleted(event: PaymentCompletedEvent, partition: Int, offset: Long) {
        concertDataPlatformClient.sendPaymentData(
            paymentId = event.paymentId,
            userId = event.userId,
            reservationId = event.reservationId,
            amount = event.amount,
            operationType = "PAYMENT_COMPLETED"
        )
    }
    
    private fun handlePaymentFailed(event: PaymentFailedEvent, partition: Int, offset: Long) {
        concertDataPlatformClient.sendPaymentData(
            paymentId = event.paymentId,
            userId = event.userId,
            reservationId = event.reservationId,
            amount = event.amount ?: java.math.BigDecimal.ZERO,
            operationType = "PAYMENT_FAILED"
        )
    }
    
    fun getProcessedCount(): Int = processedCount.get()
    
    fun resetProcessedCount() {
        processedCount.set(0)
    }
}