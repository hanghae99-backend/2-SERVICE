package kr.hhplus.be.server.balance.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.hhplus.be.server.balance.entity.Point
import java.math.BigDecimal

@Schema(description = "잔액 조회 응답")
data class BalanceResponse(
    @Schema(description = "사용자 ID", example = "1")
    val userId: Long,
    
    @Schema(description = "현재 잔액", example = "250000")
    val balance: BigDecimal,
    
    @Schema(description = "마지막 업데이트 시간", example = "2024-01-01T15:00:00")
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
