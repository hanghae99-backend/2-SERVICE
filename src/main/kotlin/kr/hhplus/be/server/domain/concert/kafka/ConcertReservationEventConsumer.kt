package kr.hhplus.be.server.domain.concert.kafka

import kr.hhplus.be.server.domain.reservation.event.ReservationCancelledEvent
import kr.hhplus.be.server.domain.reservation.event.ReservationCreatedEvent
import kr.hhplus.be.server.domain.concert.service.ConcertService
import kr.hhplus.be.server.domain.concert.service.SeatService
import mu.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

@Component
class ConcertReservationEventConsumer(
    private val seatService: SeatService,
    private val concertService: ConcertService
) {
    private val logger = KotlinLogging.logger {}
    private val processedCount = AtomicInteger(0)
    
    @KafkaListener(
        topics = ["reservation-events"],
        groupId = "concert-consumer",
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
            logger.error(e) { "콘서트 이벤트 처리 실패: partition=$partition, offset=$offset" }
            throw e
        }
    }
    
    private fun handleReservationCreated(event: ReservationCreatedEvent, partition: Int, offset: Long) {
        try {
            concertService.incrementPopularity(event.concertId)
        } catch (e: Exception) {
            logger.error(e) { "콘서트 인기도 증가 실패: concertId=${event.concertId}" }
            throw e
        }
    }
    
    private fun handleReservationCancelled(event: ReservationCancelledEvent, partition: Int, offset: Long) {
        try {
            seatService.releaseSeat(event.seatId)
            concertService.decrementPopularity(event.concertId)
        } catch (e: Exception) {
            logger.error(e) { "예약 취소 처리 실패: seatId=${event.seatId}" }
            throw e
        }
    }
}