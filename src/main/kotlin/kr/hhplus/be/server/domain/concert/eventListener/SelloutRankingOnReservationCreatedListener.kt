package kr.hhplus.be.server.domain.concert.eventListener

import kr.hhplus.be.server.domain.reservation.event.ReservationCreatedEvent
import kr.hhplus.be.server.domain.reservation.service.SelloutRankingService
import kr.hhplus.be.server.global.event.EventErrorHandler
import kr.hhplus.be.server.global.event.EventErrorHandling
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SelloutRankingOnReservationCreatedListener(
    private val selloutRankingService: SelloutRankingService,
    private val errorHandler: EventErrorHandler
) {
    
    private val logger = KotlinLogging.logger {}
    
    @EventErrorHandling(sendToDLQ = false, critical = false)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: ReservationCreatedEvent) {
        errorHandler.handleEventSafely(event, "ReservationCreatedEvent") {
            selloutRankingService.incrementReservationCount(event.concertId)
            logger.info { "매진 순위 업데이트 완료 - concertId: ${event.concertId}" }
        }
    }
}