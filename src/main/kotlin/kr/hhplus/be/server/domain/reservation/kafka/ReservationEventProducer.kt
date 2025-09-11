package kr.hhplus.be.server.domain.reservation.kafka

import kr.hhplus.be.server.domain.reservation.event.ReservationCancelledEvent
import kr.hhplus.be.server.domain.reservation.event.ReservationCreatedEvent
import mu.KotlinLogging
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class ReservationEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val logger = KotlinLogging.logger {}
    
    companion object {
        private const val RESERVATION_EVENTS_TOPIC = "reservation-events"
    }
    
    fun sendReservationCreatedEvent(event: ReservationCreatedEvent) {
        try {
            kafkaTemplate.send(RESERVATION_EVENTS_TOPIC, event.reservationId.toString(), event)
                .exceptionally { throwable ->
                    logger.error(throwable) { "예약 생성 이벤트 전송 실패: reservationId=${event.reservationId}" }
                    null
                }
        } catch (e: Exception) {
            logger.error(e) { "예약 생성 이벤트 전송 중 예외 발생: reservationId=${event.reservationId}" }
            throw e
        }
    }
    
    fun sendReservationCancelledEvent(event: ReservationCancelledEvent) {
        try {
            val key = event.reservationId?.toString() ?: event.userId.toString()
            kafkaTemplate.send(RESERVATION_EVENTS_TOPIC, key, event)
                .exceptionally { throwable ->
                    logger.error(throwable) { "예약 취소 이벤트 전송 실패: reservationId=${event.reservationId}" }
                    null
                }
        } catch (e: Exception) {
            logger.error(e) { "예약 취소 이벤트 전송 중 예외 발생: reservationId=${event.reservationId}" }
            throw e
        }
    }
    
    fun getTopicInfo(): String = RESERVATION_EVENTS_TOPIC
}