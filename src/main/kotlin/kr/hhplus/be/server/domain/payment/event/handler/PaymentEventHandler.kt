package kr.hhplus.be.server.domain.payment.event.handler

import kr.hhplus.be.server.domain.payment.event.PaymentCompletedEvent
import kr.hhplus.be.server.domain.payment.event.PaymentFailedEvent
import kr.hhplus.be.server.domain.concert.service.ConcertDataPlatformService
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class PaymentEventHandler(
    private val concertDataPlatformService: ConcertDataPlatformService
) {
    private val logger = KotlinLogging.logger {}
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handlePaymentCompleted(event: PaymentCompletedEvent) {
        logger.info { "결제 완료 처리 시작 - paymentId: ${event.paymentId}, reservationId: ${event.reservationId}" }

        concertDataPlatformService.sendReservationData(
            reservationId = event.reservationId,
            userId = event.userId,
            concertId = event.concertId,
            seatId = event.seatId,
            paymentId = event.paymentId,
            operationType = "PAYMENT_COMPLETED"
        )
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handlePaymentFailed(event: PaymentFailedEvent) {
        logger.info { "결제 실패 처리 - paymentId: ${event.paymentId}, reason: ${event.reason}" }
    }
}