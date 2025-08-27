package kr.hhplus.be.server.domain.payment.event.listener

import kr.hhplus.be.server.domain.payment.event.PaymentFailedEvent
import kr.hhplus.be.server.domain.payment.event.PaymentFailureStage
import kr.hhplus.be.server.domain.payment.event.BalanceRestoreRequiredEvent
import kr.hhplus.be.server.global.event.DomainEventPublisher
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 결제 실패 시 보상 트랜잭션을 처리하는 리스너
 * 잔고 복원 이벤트 발행을 담당
 */
@Component
class PaymentFailedEventListener(
    private val eventPublisher: DomainEventPublisher
) {
    private val logger = KotlinLogging.logger {}
    
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: PaymentFailedEvent) {
        try {
            // 잔고 복원이 필요한 경우 이벤트 발행
            when (event.failureStage) {
                PaymentFailureStage.RESERVATION_CONFIRM -> {
                    if (event.needsBalanceRestore && event.amount != null) {
                        eventPublisher.publish(
                            BalanceRestoreRequiredEvent(
                                userId = event.userId,
                                amount = event.amount,
                                paymentId = event.paymentId,
                                reservationId = event.reservationId,
                                reason = "예약 확정 실패로 인한 잔고 복원: ${event.reason}"
                            )
                        )
                    }
                }
                else -> {
                    logger.warn { "복구 불가 실패: payment=${event.paymentId}, stage=${event.failureStage}" }
                }
            }
            
            
        } catch (e: Exception) {
            logger.error(e) { "결제 실패 이벤트 처리 실패: payment=${event.paymentId}" }
        }
    }
}