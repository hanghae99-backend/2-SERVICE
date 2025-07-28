package kr.hhplus.be.server.balance.event.handler

import kr.hhplus.be.server.balance.event.*
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class BalanceEventHandler {
    
    private val logger = KotlinLogging.logger {}
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleBalanceCharged(event: BalanceChargedEvent) {
        logger.info { "Balance charged - User: ${event.userId}, Amount: ${event.amount}, New Balance: ${event.newBalance}" }
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleBalanceDeducted(event: BalanceDeductedEvent) {
        logger.info { "Balance deducted - User: ${event.userId}, Amount: ${event.amount}, Remaining: ${event.remainingBalance}" }
    }
}
