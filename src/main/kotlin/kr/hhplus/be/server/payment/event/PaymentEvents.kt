package kr.hhplus.be.server.payment.event

import kr.hhplus.be.server.global.event.AbstractDomainEvent
import java.math.BigDecimal

/**
 * 결제가 성공적으로 완료되었을 때 발행되는 이벤트
 * 
 * 이 이벤트를 통해:
 * - 예약 확정 처리
 * - 좌석 상태 업데이트  
 * - 토큰 완료 처리
 * - 알림 발송 등의 후속 작업이 비동기로 처리됩니다
 */
data class PaymentCompletedEvent(
    val paymentId: Long,
    val userId: Long,
    val reservationId: Long,
    val seatId: Long,
    val amount: BigDecimal,
    val token: String
) : AbstractDomainEvent() {
    override val eventType: String = "PaymentCompleted"
}

/**
 * 결제가 실패했을 때 발행되는 이벤트
 */
data class PaymentFailedEvent(
    val paymentId: Long,
    val userId: Long,
    val reservationId: Long,
    val reason: String,
    val token: String
) : AbstractDomainEvent() {
    override val eventType: String = "PaymentFailed"
}