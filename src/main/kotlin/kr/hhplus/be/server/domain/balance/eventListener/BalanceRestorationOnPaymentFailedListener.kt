package kr.hhplus.be.server.domain.balance.eventListener

import kr.hhplus.be.server.domain.balance.service.BalanceService
import kr.hhplus.be.server.domain.payment.event.PaymentFailedEvent
import kr.hhplus.be.server.domain.payment.event.PaymentFailureStage
import kr.hhplus.be.server.global.event.EventErrorHandler
import kr.hhplus.be.server.global.event.EventErrorHandling
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class BalanceRestorationOnPaymentFailedListener(
    private val balanceService: BalanceService,
    private val errorHandler: EventErrorHandler
) {

    private val logger = KotlinLogging.logger {}

    @EventErrorHandling(sendToDLQ = true, maxRetries = 3, critical = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: PaymentFailedEvent) {
        errorHandler.handleEventSafely(event, "PaymentFailedEvent") {
            if (shouldRestoreBalance(event)) {
                restoreBalance(event)
                logger.info { "잔고 복원 완료 - userId: ${event.userId}, amount: ${event.amount}" }
            }
        }
    }

    private fun shouldRestoreBalance(event: PaymentFailedEvent): Boolean {
        return event.failureStage == PaymentFailureStage.RESERVATION_CONFIRM && event.amount != null
    }

    private fun restoreBalance(event: PaymentFailedEvent) {
        balanceService.restoreBalance(
            userId = event.userId,
            amount = event.amount!!,
            description = event.reason
        )
    }
}
