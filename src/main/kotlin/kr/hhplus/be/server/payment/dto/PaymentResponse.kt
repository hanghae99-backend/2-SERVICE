package kr.hhplus.be.server.payment.dto

import kr.hhplus.be.server.payment.entity.Payment
import java.math.BigDecimal

data class PaymentResponse(
    val paymentId: Long,
    val userId: Long,
    val reservationId: Long,
    val amount: BigDecimal,
    val status: String,
    val paidAt: String?,
    val createdAt: String
) {
    companion object {
        fun from(payment: Payment): PaymentResponse {
            return PaymentResponse(
                paymentId = payment.paymentId,
                userId = payment.userId,
                reservationId = payment.reservationId,
                amount = payment.amount,
                status = payment.status.name,
                paidAt = payment.paidAt?.toString(),
                createdAt = payment.createdAt.toString()
            )
        }
    }
}
