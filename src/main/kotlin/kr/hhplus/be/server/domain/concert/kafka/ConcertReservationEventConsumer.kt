package kr.hhplus.be.server.domain.concert.kafka

import kr.hhplus.be.server.domain.reservation.event.ReservationCancelledEvent
import kr.hhplus.be.server.domain.reservation.event.ReservationCreatedEvent
import kr.hhplus.be.server.global.client.SeatApiClient
import kr.hhplus.be.server.domain.concert.service.ConcertService
import mu.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

@Component
class ConcertReservationEventConsumer(
    private val seatApiClient: SeatApiClient,
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
            
            val count = processedCount.incrementAndGet()
            logger.debug { "콘서트 이벤트 처리 완료: count=$count, partition=$partition, offset=$offset" }
            
        } catch (e: Exception) {
            logger.error(e) { "콘서트 이벤트 처리 실패: partition=$partition, offset=$offset" }
            throw e
        }
    }
    
    private fun handleReservationCreated(event: ReservationCreatedEvent, partition: Int, offset: Long) {
        logger.info { "예약 생성으로 인한 콘서트 인기도 증가: concertId=${event.concertId}, partition=$partition, offset=$offset" }
        
        try {
            concertService.incrementPopularity(event.concertId)
            logger.info { "콘서트 인기도 증가 완료: concertId=${event.concertId}" }
            
        } catch (e: Exception) {
            logger.error(e) { "콘서트 인기도 증가 실패: concertId=${event.concertId}" }
            throw e
        }
    }
    
    private fun handleReservationCancelled(event: ReservationCancelledEvent, partition: Int, offset: Long) {
        logger.info { "예약 취소 처리: seatId=${event.seatId}, concertId=${event.concertId}, partition=$partition, offset=$offset" }
        
        try {
            // 좌석 해제
            seatApiClient.releaseSeat(event.seatId)
            logger.info { "좌석 해제 완료: seatId=${event.seatId}" }
            
            // 콘서트 인기도 감소
            concertService.decrementPopularity(event.concertId)
            logger.info { "콘서트 인기도 감소 완료: concertId=${event.concertId}" }
            
        } catch (e: Exception) {
            logger.error(e) { "예약 취소 처리 실패: seatId=${event.seatId}, concertId=${event.concertId}" }
            throw e
        }
    }
    
    fun getProcessedCount(): Int = processedCount.get()
    
    fun resetProcessedCount() {
        processedCount.set(0)
    }
}