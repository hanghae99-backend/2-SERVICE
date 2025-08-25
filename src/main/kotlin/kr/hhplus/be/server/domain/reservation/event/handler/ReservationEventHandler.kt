package kr.hhplus.be.server.domain.reservation.event.handler

import kr.hhplus.be.server.domain.reservation.event.ReservationCancelledEvent
import kr.hhplus.be.server.domain.reservation.event.ReservationConfirmedEvent
import kr.hhplus.be.server.domain.reservation.event.ReservationCreatedEvent
import kr.hhplus.be.server.domain.reservation.event.ReservationExpiredEvent
import kr.hhplus.be.server.domain.reservation.service.SelloutRankingService
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ReservationEventHandler(
    private val selloutRankingService: SelloutRankingService
) {
    
    private val logger = KotlinLogging.logger {}
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleReservationCreated(event: ReservationCreatedEvent) {
        try {
            selloutRankingService.incrementReservationCount(event.concertId)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to update sellout ranking for concert: ${event.concertId}" }
        }
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleReservationConfirmed(event: ReservationConfirmedEvent) {
        // 예약 확정 후 부가 작업들
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleReservationCancelled(event: ReservationCancelledEvent) {
        try {
            selloutRankingService.decrementReservationCount(event.concertId)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to update sellout ranking for concert: ${event.concertId}" }
        }
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleReservationExpired(event: ReservationExpiredEvent) {
        // 예약 만료 후 부가 작업들
    }
}
