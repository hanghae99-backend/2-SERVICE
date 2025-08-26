package kr.hhplus.be.server.domain.balance.event.handler

import kr.hhplus.be.server.api.balance.usecase.RestoreBalanceUseCase
import kr.hhplus.be.server.domain.payment.event.BalanceRestoreRequiredEvent
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class BalanceEventHandler(
    private val restoreBalanceUseCase: RestoreBalanceUseCase
) {
    private val logger = KotlinLogging.logger {}
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleBalanceRestoreRequired(event: BalanceRestoreRequiredEvent) {
        logger.info { "잔고 복원 요청 처리 시작 - userId: ${event.userId}, amount: ${event.amount}, paymentId: ${event.paymentId}" }
        
        try {
            restoreBalanceUseCase.execute(
                userId = event.userId,
                amount = event.amount,
                reason = event.reason,
                paymentId = event.paymentId
            )
            
            logger.info { "잔고 복원 완료 - userId: ${event.userId}, amount: ${event.amount}, paymentId: ${event.paymentId}" }
            
        } catch (e: Exception) {
            logger.error(e) { "잔고 복원 실패 - userId: ${event.userId}, amount: ${event.amount}, paymentId: ${event.paymentId}" }
            
            // 복원 실패 시 알림이나 추가 처리 로직 필요시 여기에 구현
            // 예: 관리자 알림, 재시도 큐 추가 등
        }
    }
}