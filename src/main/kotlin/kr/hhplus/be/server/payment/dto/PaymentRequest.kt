package kr.hhplus.be.server.payment.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

@Schema(description = "결제 요청")
data class PaymentRequest(
    @field:NotNull(message = "사용자 ID는 필수입니다")
    @field:Positive(message = "사용자 ID는 양수여야 합니다")
    @Schema(description = "사용자 ID", example = "1")
    val userId: Long,
    
    @field:NotNull(message = "예약 ID는 필수입니다")
    @field:Positive(message = "예약 ID는 양수여야 합니다")
    @Schema(description = "예약 ID", example = "1")
    val reservationId: Long,
    
    @field:NotBlank(message = "토큰은 필수입니다")
    @field:Size(min = 10, max = 100, message = "토큰 길이는 10-100자 사이여야 합니다")
    @Schema(description = "대기열 토큰", example = "token-uuid-1234")
    val token: String
)
