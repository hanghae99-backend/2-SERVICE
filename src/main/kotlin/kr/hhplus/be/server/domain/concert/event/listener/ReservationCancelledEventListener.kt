package kr.hhplus.be.server.domain.concert.event.listener

import kr.hhplus.be.server.domain.reservation.event.ReservationCancelledEvent
import kr.hhplus.be.server.domain.reservation.service.SelloutRankingService
import mu.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ReservationCancelledEventListener(
    private val selloutRankingService: SelloutRankingService
) {
    
    private val logger = KotlinLogging.logger {}
    
    @EventListener
    fun handle(event: ReservationCancelledEvent) {
        try {
            // 매진 순위 업데이트 (예약 감소)
            selloutRankingService.decrementReservationCount(event.concertId)
            logger.info { "콘서트 매진 순위 업데이트 완료 - concertId: ${event.concertId}, reservationId: ${event.reservationId}" }
            
        } catch (e: Exception) {
            logger.error(e) { "콘서트 매진 순위 업데이트 실패 - concertId: ${event.concertId}, reservationId: ${event.reservationId}" }
        }
    }
}