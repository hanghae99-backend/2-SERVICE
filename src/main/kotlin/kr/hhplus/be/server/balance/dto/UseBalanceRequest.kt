package kr.hhplus.be.server.balance.dto

import java.math.BigDecimal

data class UseBalanceRequest(
    val userId: Long,
    val amount: BigDecimal
) {
    init {
        require(amount > BigDecimal.ZERO) { "사용 금액은 0보다 커야 합니다" }
    }
}
