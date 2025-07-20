package kr.hhplus.be.server.balance.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

@Schema(description = "잔액 충전 요청")
data class ChargeBalanceRequest(

    @NotNull(message = "사용자 ID는 필수입니다")
    @Positive(message = "사용자 ID는 양수여야 합니다")
    @Schema(description = "사용자 ID", example = "1")
    val userId: Long,

    @NotNull(message = "충전 금액은 필수입니다")
    @DecimalMin(value = "1000.0", message = "최소 충전 금액은 1,000원입니다")
    @DecimalMax(value = "10000000.0", message = "최대 충전 금액은 10,000,000원입니다")
    @Schema(description = "충전할 금액", example = "100000")
    val amount: BigDecimal
)
