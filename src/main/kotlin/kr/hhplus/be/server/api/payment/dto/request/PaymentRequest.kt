package kr.hhplus.be.server.api.payment.dto.request

@io.swagger.v3.oas.annotations.media.Schema(description = "결제 요청")
data class PaymentRequest(
    @jakarta.validation.constraints.NotNull(message = "사용자 ID는 필수입니다")
    @jakarta.validation.constraints.Positive(message = "사용자 ID는 양수여야 합니다")
    @io.swagger.v3.oas.annotations.media.Schema(description = "사용자 ID", example = "1")
    val userId: Long,

    @jakarta.validation.constraints.NotNull(message = "예약 ID는 필수입니다")
    @jakarta.validation.constraints.Positive(message = "예약 ID는 양수여야 합니다")
    @io.swagger.v3.oas.annotations.media.Schema(description = "예약 ID", example = "1")
    val reservationId: Long,

    @jakarta.validation.constraints.NotBlank(message = "토큰은 필수입니다")
    @jakarta.validation.constraints.Size(min = 10, max = 100, message = "토큰 길이는 10-100자 사이여야 합니다")
    @io.swagger.v3.oas.annotations.media.Schema(description = "대기열 토큰", example = "token-uuid-1234")
    val token: String
)