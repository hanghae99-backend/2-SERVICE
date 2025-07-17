package kr.hhplus.be.server.payment.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "결제 요청")
data class PaymentRequest(
    @Schema(description = "사용자 ID", example = "1")
    val userId: Long,
    
    @Schema(description = "예약 ID", example = "1")
    val reservationId: Long,
    
    @Schema(description = "대기열 토큰", example = "token-uuid-1234")
    val token: String
) {
    init {
        require(userId > 0) { "사용자 ID는 0보다 커야 합니다" }
        require(reservationId > 0) { "예약 ID는 0보다 커야 합니다" }
        require(token.isNotBlank()) { "토큰은 비어있을 수 없습니다" }
    }
}
