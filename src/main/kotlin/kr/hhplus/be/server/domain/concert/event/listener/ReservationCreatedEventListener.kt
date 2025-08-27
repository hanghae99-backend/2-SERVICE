package kr.hhplus.be.server.domain.concert.event.listener

import kr.hhplus.be.server.domain.reservation.event.ReservationCreatedEvent
import kr.hhplus.be.server.domain.reservation.service.SelloutRankingService
import mu.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ReservationCreatedEventListener(
    private val selloutRankingService: SelloutRankingService
) {
    
    private val logger = KotlinLogging.logger {}
    
    @EventListener
    fun handle(event: ReservationCreatedEvent) {
        try {
            // 매진 순위 업데이트 (예약 증가)
            selloutRankingService.incrementReservationCount(event.concertId)
            logger.info { "콘서트 매진 순위 업데이트 완료 - concertId: ${event.concertId}, reservationId: ${event.reservationId}" }
            
        } catch (e: Exception) {
            logger.error(e) { "콘서트 매진 순위 업데이트 실패 - concertId: ${event.concertId}, reservationId: ${event.reservationId}" }
            // 매진 순위는 비즈니스 핵심 로직이 아니므로 예외를 던지지 않음
        }
    }
}