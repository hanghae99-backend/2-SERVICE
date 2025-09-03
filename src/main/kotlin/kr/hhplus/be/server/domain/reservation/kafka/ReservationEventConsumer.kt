package kr.hhplus.be.server.domain.reservation.kafka

import kr.hhplus.be.server.domain.reservation.event.ReservationCancelledEvent
import kr.hhplus.be.server.domain.reservation.event.ReservationCreatedEvent
import kr.hhplus.be.server.global.client.ConcertDataPlatformClient
import mu.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

@Component
class ReservationEventConsumer(
    private val concertDataPlatformClient: ConcertDataPlatformClient
) {
    private val logger = KotlinLogging.logger {}
    private val processedCount = AtomicInteger(0)
    
    @KafkaListener(
        topics = ["reservation-events"],
        groupId = "reservation-consumer",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleReservationEvent(
        @Payload event: Any,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        @Header(name = "kafka_receivedMessageKey", required = false) key: String?
    ) {
        try {
            when (event) {
                is ReservationCreatedEvent -> handleReservationCreated(event, partition, offset)
                is ReservationCancelledEvent -> handleReservationCancelled(event, partition, offset)
                else -> logger.warn { "알 수 없는 예약 이벤트 타입: ${event::class.simpleName}" }
            }
            
            processedCount.incrementAndGet()
            
        } catch (e: Exception) {
            logger.error(e) { "예약 이벤트 처리 실패: partition=$partition, offset=$offset" }
            throw e
        }
    }
    
    private fun handleReservationCreated(event: ReservationCreatedEvent, partition: Int, offset: Long) {
        try {
            concertDataPlatformClient.sendReservationData(
                reservationId = event.reservationId,
                userId = event.userId,
                concertId = event.concertId,
                seatId = event.seatId,
                operationType = "RESERVATION_CREATED"
            )
        } catch (e: Exception) {
            logger.error(e) { "예약 생성 이벤트 처리 실패: reservationId=${event.reservationId}" }
            throw e
        }
    }
    
    private fun handleReservationCancelled(event: ReservationCancelledEvent, partition: Int, offset: Long) {
        try {
            concertDataPlatformClient.sendReservationData(
                reservationId = event.reservationId ?: 0L,
                userId = event.userId,
                concertId = event.concertId,
                seatId = event.seatId,
                operationType = "RESERVATION_CANCELLED"
            )
        } catch (e: Exception) {
            logger.error(e) { "예약 취소 이벤트 처리 실패: reservationId=${event.reservationId}" }
            throw e
        }
    }
    
    fun getProcessedCount(): Int = processedCount.get()
    
    fun resetProcessedCount() {
        processedCount.set(0)
    }
}