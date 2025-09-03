package kr.hhplus.be.server.domain.payment.kafka

import kr.hhplus.be.server.domain.payment.event.PaymentCompletedEvent
import kr.hhplus.be.server.domain.payment.event.PaymentFailedEvent
import mu.KotlinLogging
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class PaymentEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val logger = KotlinLogging.logger {}
    
    companion object {
        private const val PAYMENT_EVENTS_TOPIC = "payment-events"
    }
    
    fun sendPaymentCompletedEvent(event: PaymentCompletedEvent) {
        try {
            kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, "completed", event.paymentId.toString(), event)
                .thenAccept { result ->
                    logger.info { "결제 완료 이벤트 전송 완료: paymentId=${event.paymentId}, offset=${result.recordMetadata.offset()}" }
                }
                .exceptionally { throwable ->
                    logger.error(throwable) { "결제 완료 이벤트 전송 실패: paymentId=${event.paymentId}" }
                    null
                }
        } catch (e: Exception) {
            logger.error(e) { "결제 완료 이벤트 전송 중 예외 발생: paymentId=${event.paymentId}" }
            throw e
        }
    }
    
    fun sendPaymentFailedEvent(event: PaymentFailedEvent) {
        try {
            val key = event.paymentId?.toString() ?: event.userId.toString()
            kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, "failed", key, event)
                .thenAccept { result ->
                    logger.info { "결제 실패 이벤트 전송 완료: paymentId=${event.paymentId}, offset=${result.recordMetadata.offset()}" }
                }
                .exceptionally { throwable ->
                    logger.error(throwable) { "결제 실패 이벤트 전송 실패: paymentId=${event.paymentId}" }
                    null
                }
        } catch (e: Exception) {
            logger.error(e) { "결제 실패 이벤트 전송 중 예외 발생: paymentId=${event.paymentId}" }
            throw e
        }
    }
    
    fun getTopicInfo(): String = PAYMENT_EVENTS_TOPIC
}