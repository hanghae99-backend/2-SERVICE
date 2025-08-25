package kr.hhplus.be.server.domain.payment.event

import kr.hhplus.be.server.global.event.AbstractDomainEvent
import java.math.BigDecimal

data class PaymentCompletedEvent(
    val paymentId: Long,
    val userId: Long,
    val reservationId: Long,
    val seatId: Long,
    val amount: BigDecimal,
    val token: String,
    val scheduleId: Long,
    val seatNumber: String,
    val concertId: Long
) : AbstractDomainEvent() {
    override val eventType: String = "PaymentCompleted"
}

data class PaymentFailedEvent(
    val paymentId: Long,
    val userId: Long,
    val reservationId: Long,
    val reason: String,
    val token: String
) : AbstractDomainEvent() {
    override val eventType: String = "PaymentFailed"
}