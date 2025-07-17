package kr.hhplus.be.server.balance.dto

import kr.hhplus.be.server.balance.entity.Point
import java.math.BigDecimal

data class BalanceResponse(
    val userId: Long,
    val balance: BigDecimal,
    val lastUpdated: String
) {
    companion object {
        fun from(point: Point): BalanceResponse {
            return BalanceResponse(
                userId = point.userId,
                balance = point.amount,
                lastUpdated = point.lastUpdated.toString()
            )
        }
    }
}
