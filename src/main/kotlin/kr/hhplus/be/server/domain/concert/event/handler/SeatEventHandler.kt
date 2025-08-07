package kr.hhplus.be.server.domain.concert.event.handler

import kr.hhplus.be.server.domain.concert.event.SeatConfirmedEvent
import kr.hhplus.be.server.domain.concert.event.SeatStatusChangedEvent
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SeatEventHandler {
    
    private val logger = KotlinLogging.logger {}
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleSeatConfirmed(event: SeatConfirmedEvent) {
        logger.info { "Seat confirmed - Seat: ${event.seatNumber}, User: ${event.userId}, Payment: ${event.paymentId}" }
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleSeatStatusChanged(event: SeatStatusChangedEvent) {
        logger.debug { "Seat status changed - Seat: ${event.seatNumber}, ${event.previousStatus} -> ${event.newStatus}" }
    }
}
