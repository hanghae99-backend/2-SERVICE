package kr.hhplus.be.server.payment.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.hhplus.be.server.payment.entity.Payment
import java.math.BigDecimal

@Schema(description = "결제 응답")
data class PaymentResponse(
    @Schema(description = "결제 ID", example = "1")
    val paymentId: Long,
    
    @Schema(description = "사용자 ID", example = "1")
    val userId: Long,
    
    @Schema(description = "예약 ID", example = "1")
    val reservationId: Long,
    
    @Schema(description = "결제 금액", example = "100000")
    val amount: BigDecimal,
    
    @Schema(description = "결제 상태", example = "COMPLETED")
    val status: String,
    
    @Schema(description = "결제 완료 시간", example = "2024-01-01T15:00:00")
    val paidAt: String?,
    
    @Schema(description = "결제 생성 시간", example = "2024-01-01T15:00:00")
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
