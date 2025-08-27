package kr.hhplus.be.server.domain.balance.event.listener

import kr.hhplus.be.server.domain.payment.event.BalanceRestoreRequiredEvent
import kr.hhplus.be.server.domain.balance.service.BalanceService
import kr.hhplus.be.server.global.event.EventErrorHandler
import kr.hhplus.be.server.global.event.EventErrorHandling
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class BalanceRestoreRequiredEventListener(
    private val balanceService: BalanceService,
    private val errorHandler: EventErrorHandler
) {
    
    private val logger = KotlinLogging.logger {}
    
    @EventErrorHandling(sendToDLQ = true, maxRetries = 3, critical = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: BalanceRestoreRequiredEvent) {
        errorHandler.handleEventSafely(event, "BalanceRestoreRequiredEvent") {
            balanceService.restoreBalance(
                userId = event.userId,
                amount = event.amount,
                description = event.reason
            )
            logger.info { "잔고 복원 완료 - userId: ${event.userId}" }
        }
    }
}