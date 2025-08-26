package kr.hhplus.be.server.domain.payment.event

import kr.hhplus.be.server.global.event.AbstractDomainEvent
import java.math.BigDecimal

data class PaymentCompletedEvent(
    val paymentId: Long,
    val userId: Long,
    val reservationId: Long,
    val amount: BigDecimal,
    val token: String
) : AbstractDomainEvent() {
    override val eventType: String = "PaymentCompleted"
}

data class PaymentFailedEvent(
    val paymentId: Long,
    val userId: Long,
    val reservationId: Long,
    val reason: String,
    val token: String,
    val amount: BigDecimal? = null,
    val needsBalanceRestore: Boolean = false,
    val failureStage: PaymentFailureStage = PaymentFailureStage.UNKNOWN
) : AbstractDomainEvent() {
    override val eventType: String = "PaymentFailed"
}

enum class PaymentFailureStage {
    BALANCE_DEDUCTION,    // 잔고 차감 실패
    RESERVATION_CONFIRM,  // 예약 확정 실패
    TOKEN_COMPLETION,     // 토큰 완료 실패
    UNKNOWN              // 알 수 없는 단계
}

/**
 * 잔고 복원 이벤트 - 결제 실패 시 차감된 잔고를 복원하기 위한 이벤트
 */
data class BalanceRestoreRequiredEvent(
    val userId: Long,
    val amount: BigDecimal,
    val paymentId: Long,
    val reservationId: Long,
    val reason: String
) : AbstractDomainEvent() {
    override val eventType: String = "BalanceRestoreRequired"
}