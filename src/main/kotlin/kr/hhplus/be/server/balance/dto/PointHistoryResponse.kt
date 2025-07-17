package kr.hhplus.be.server.balance.dto

import kr.hhplus.be.server.balance.entity.PointHistory
import java.math.BigDecimal

data class PointHistoryResponse(
    val historyId: Long,
    val userId: Long,
    val amount: BigDecimal,
    val type: String,
    val description: String,
    val createdAt: String
) {
    companion object {
        fun from(history: PointHistory): PointHistoryResponse {
            return PointHistoryResponse(
                historyId = history.historyId,
                userId = history.userId,
                amount = history.amount,
                type = history.type.name,
                description = history.description,
                createdAt = history.createdAt.toString()
            )
        }
    }
}
