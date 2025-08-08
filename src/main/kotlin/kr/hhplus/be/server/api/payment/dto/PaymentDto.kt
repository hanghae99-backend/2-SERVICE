package kr.hhplus.be.server.api.payment.dto

import kr.hhplus.be.server.domain.payment.models.Payment
import kr.hhplus.be.server.domain.reservation.model.Reservation
import java.math.BigDecimal
import java.time.LocalDateTime

data class PaymentDto(
    val paymentId: Long,
    val userId: Long,
    val amount: BigDecimal,
    val paymentMethod: String?,
    val statusCode: String,
    val paidAt: LocalDateTime?,
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
            )
        }
    }
}