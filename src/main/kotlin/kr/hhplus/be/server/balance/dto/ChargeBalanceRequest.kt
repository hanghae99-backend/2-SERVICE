package kr.hhplus.be.server.balance.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "잔액 충전 요청")
data class ChargeBalanceRequest(
    @Schema(description = "사용자 ID", example = "1")
    val userId: Long,
    
    @Schema(description = "충전할 금액", example = "100000")
    val amount: BigDecimal
) {
    init {
        require(amount > BigDecimal.ZERO) { "충전 금액은 0보다 커야 합니다" }
    }
}
