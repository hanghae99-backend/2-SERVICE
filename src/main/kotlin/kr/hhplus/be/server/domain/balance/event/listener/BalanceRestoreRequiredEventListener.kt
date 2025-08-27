package kr.hhplus.be.server.domain.balance.event.listener

import kr.hhplus.be.server.domain.payment.event.BalanceRestoreRequiredEvent
import kr.hhplus.be.server.domain.balance.service.BalanceService
import mu.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class BalanceRestoreRequiredEventListener(
    private val balanceService: BalanceService
) {
    
    private val logger = KotlinLogging.logger {}
    
    @EventListener
    fun handle(event: BalanceRestoreRequiredEvent) {
        try {
            // 잔고 복원 처리
            balanceService.restoreBalance(
                userId = event.userId,
                amount = event.amount,
                description = event.reason
            )
            
            logger.info { "잔고 복원 처리 완료 - userId: ${event.userId}, amount: ${event.amount}, paymentId: ${event.paymentId}" }
            
        } catch (e: Exception) {
            logger.error(e) { "잔고 복원 처리 실패 - userId: ${event.userId}, amount: ${event.amount}, paymentId: ${event.paymentId}" }
            // TODO: 알림 시스템 연동 또는 수동 처리 대상으로 등록
        }
    }
}