package kr.hhplus.be.server.balance.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.hhplus.be.server.balance.entity.PointHistory
import java.math.BigDecimal

@Schema(description = "포인트 이력 응답")
data class PointHistoryResponse(
    @Schema(description = "이력 ID", example = "1")
    val historyId: Long,
    
    @Schema(description = "사용자 ID", example = "1")
    val userId: Long,
    
    @Schema(description = "금액", example = "100000")
    val amount: BigDecimal,
    
    @Schema(description = "유형", example = "CHARGE")
    val type: String,
    
    @Schema(description = "설명", example = "포인트 충전")
    val description: String,
    
    @Schema(description = "생성 시간", example = "2024-01-01T15:00:00")
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
