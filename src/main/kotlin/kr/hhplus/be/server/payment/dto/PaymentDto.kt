package kr.hhplus.be.server.payment.dto

import kr.hhplus.be.server.payment.entity.Payment
import kr.hhplus.be.server.reservation.entity.Reservation
import java.math.BigDecimal
import java.time.LocalDateTime

data class PaymentDto(
    val paymentId: Long,
    val userId: Long,
    val amount: BigDecimal,
    val paymentMethod: String?,
    val statusCode: String,
    val paidAt: LocalDateTime?,
    val reservationList: List<Reservation>
) {
    companion object {
        fun fromEntity(payment: Payment): PaymentDto {
            return PaymentDto(
                paymentId = payment.paymentId,
                userId = payment.userId,
                amount = payment.amount,
                paymentMethod = payment.paymentMethod,
                statusCode = payment.status.code,
                paidAt = payment.paidAt,
                reservationList = payment.reservationList
            )
        }
    }
}