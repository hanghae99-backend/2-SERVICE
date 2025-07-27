package kr.hhplus.be.server.reservation.event.handler

import kr.hhplus.be.server.reservation.event.*
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ReservationEventHandler {
    
    private val logger = KotlinLogging.logger {}
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleReservationCreated(event: ReservationCreatedEvent) {
        logger.info { "Reservation created - ID: ${event.reservationId}, User: ${event.userId}, Seat: ${event.seatNumber}" }
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleReservationConfirmed(event: ReservationConfirmedEvent) {
        logger.info { "Reservation confirmed - ID: ${event.reservationId}, User: ${event.userId}, Payment: ${event.paymentId}" }
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleReservationCancelled(event: ReservationCancelledEvent) {
        logger.info { "Reservation cancelled - ID: ${event.reservationId}, Reason: ${event.cancelReason}, Expired: ${event.isExpired}" }
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleReservationExpired(event: ReservationExpiredEvent) {
        logger.info { "Reservation expired - ID: ${event.reservationId}, User: ${event.userId}" }
    }
}
