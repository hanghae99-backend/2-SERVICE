package kr.hhplus.be.server.domain.payment.event.handler

import kr.hhplus.be.server.domain.payment.event.PaymentCompletedEvent
import kr.hhplus.be.server.domain.payment.event.PaymentFailedEvent
import kr.hhplus.be.server.domain.payment.event.PaymentFailureStage
import kr.hhplus.be.server.domain.payment.event.BalanceRestoreRequiredEvent
import kr.hhplus.be.server.global.event.DomainEventPublisher
import kr.hhplus.be.server.global.client.BalanceApiClient
import kr.hhplus.be.server.global.client.ReservationApiClient
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class PaymentEventHandler(
    private val eventPublisher: DomainEventPublisher,
    private val balanceApiClient: BalanceApiClient,
    private val reservationApiClient: ReservationApiClient,
    private val tokenLifecycleManager: TokenLifecycleManager
) {
    private val logger = KotlinLogging.logger {}
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handlePaymentCompleted(event: PaymentCompletedEvent) {
        try {
            balanceApiClient.deductBalance(
                userId = event.userId,
                amount = event.amount,
                description = "콘서트 티켓 결제 - 예약ID: ${event.reservationId}"
            )

            reservationApiClient.confirmReservation(
                reservationId = event.reservationId,
                paymentId = event.paymentId
            )

            tokenLifecycleManager.completeToken(event.token)
            
            logger.info { "결제 완료 처리 성공 - paymentId: ${event.paymentId}" }

        } catch (e: Exception) {
            logger.error(e) { "결제 완료 처리 실패 - paymentId: ${event.paymentId}" }
            
            val (failureReason, failureStage, needsRestore) = when {
                e.message?.contains("잔고") == true -> 
                    Triple("잔고 차감 실패: ${e.message}", PaymentFailureStage.BALANCE_DEDUCTION, false)
                e.message?.contains("예약") == true -> 
                    Triple("예약 확정 실패: ${e.message}", PaymentFailureStage.RESERVATION_CONFIRM, true)
                e.message?.contains("토큰") == true -> 
                    Triple("토큰 완료 실패: ${e.message}", PaymentFailureStage.TOKEN_COMPLETION, false)
                else -> 
                    Triple("결제 처리 실패: ${e.message}", PaymentFailureStage.UNKNOWN, true)
            }
            
            eventPublisher.publish(PaymentFailedEvent(
                paymentId = event.paymentId,
                userId = event.userId,
                reservationId = event.reservationId,
                reason = failureReason,
                token = event.token,
                amount = event.amount,
                needsBalanceRestore = needsRestore,
                failureStage = failureStage
            ))
        }
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handlePaymentFailed(event: PaymentFailedEvent) {
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
                    logger.info { "잔고 복원 이벤트 발행 - paymentId: ${event.paymentId}" }
                }
            }
            else -> {
                logger.error { "복구할 수 없는 실패 - paymentId: ${event.paymentId}, stage: ${event.failureStage}" }
            }
        }
    }
}