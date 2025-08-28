package kr.hhplus.be.server.domain.concert.eventListener

import kr.hhplus.be.server.domain.reservation.event.ReservationCancelledEvent
import kr.hhplus.be.server.domain.reservation.service.SelloutRankingService
import kr.hhplus.be.server.global.event.EventErrorHandler
import kr.hhplus.be.server.global.event.EventErrorHandling
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SelloutRankingOnReservationCancelledListener(
    private val selloutRankingService: SelloutRankingService,
    private val errorHandler: EventErrorHandler
) {
    
    private val logger = KotlinLogging.logger {}
    
    @EventErrorHandling(sendToDLQ = false, critical = false)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: ReservationCancelledEvent) {
        errorHandler.handleEventSafely(event, "ReservationCancelledEvent") {
            selloutRankingService.decrementReservationCount(event.concertId)
            logger.info { "매진 순위 업데이트 완료 - concertId: ${event.concertId}" }
        }
    }
}