package kr.hhplus.be.server.api.balance.dto.request

@io.swagger.v3.oas.annotations.media.Schema(description = "잔액 충전 요청")
data class ChargeBalanceRequest(

    @jakarta.validation.constraints.NotNull(message = "사용자 ID는 필수입니다")
    @jakarta.validation.constraints.Positive(message = "사용자 ID는 양수여야 합니다")
    @io.swagger.v3.oas.annotations.media.Schema(description = "사용자 ID", example = "1")
    val userId: Long,

    @jakarta.validation.constraints.NotNull(message = "충전 금액은 필수입니다")
    @jakarta.validation.constraints.DecimalMin(value = "1000.0", message = "최소 충전 금액은 1,000원입니다")
    @jakarta.validation.constraints.DecimalMax(value = "10000000.0", message = "최대 충전 금액은 10,000,000원입니다")
    @io.swagger.v3.oas.annotations.media.Schema(description = "충전할 금액", example = "100000")
    val amount: java.math.BigDecimal
)