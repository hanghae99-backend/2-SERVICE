package kr.hhplus.be.server.api.payment.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal

@Schema(description = "결제 요청")
data class PaymentRequest(
    @field:NotNull(message = "사용자 ID는 필수입니다")
    @field:Positive(message = "사용자 ID는 양수여야 합니다")
    @Schema(description = "사용자 ID", example = "1", required = true)
    val userId: Long,

    @field:NotNull(message = "예약 ID는 필수입니다")
    @field:Positive(message = "예약 ID는 양수여야 합니다")
    @Schema(description = "예약 ID", example = "1", required = true)
    val reservationId: Long,

    @field:NotNull(message = "좌석 ID는 필수입니다")
    @field:Positive(message = "좌석 ID는 양수여야 합니다")
    @Schema(description = "좌석 ID", example = "1", required = true)
    val seatId: Long,

    @field:NotNull(message = "결제 금액은 필수입니다")
    @field:DecimalMin(value = "1000.0", message = "최소 결제 금액은 1,000원입니다")
    @field:DecimalMax(value = "10000000.0", message = "최대 결제 금액은 10,000,000원입니다")
    @Schema(description = "결제 금액 (KRW)", example = "50000", required = true)
    val amount: BigDecimal,

    @field:NotBlank(message = "토큰은 필수입니다")
    @field:Size(min = 10, max = 100, message = "토큰 길이는 10-100자 사이여야 합니다")
    @Schema(description = "대기열 토큰", example = "AT_1234567890ABCDEF", required = true)
    val token: String
)
