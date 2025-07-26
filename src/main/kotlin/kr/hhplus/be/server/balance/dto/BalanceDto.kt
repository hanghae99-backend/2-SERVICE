package kr.hhplus.be.server.balance.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.hhplus.be.server.balance.entity.Point
import kr.hhplus.be.server.balance.entity.PointHistory
import java.math.BigDecimal

data class BalanceDto(
    @Schema(description = "사용자 ID", example = "1")
    val userId: Long,
    
    @Schema(description = "현재 잔액", example = "250000")
    val balance: BigDecimal
) {
    companion object {
        fun from(point: Point): BalanceDto {
            return BalanceDto(
                userId = point.userId,
                balance = point.amount
            )
        }
    }

    @Schema(description = "잔액 상세 조회 응답")
    data class Detail(
        @Schema(description = "사용자 ID", example = "1")
        val userId: Long,
        
        @Schema(description = "현재 잔액", example = "250000")
        val balance: BigDecimal,
        
        @Schema(description = "마지막 업데이트 시간", example = "2024-01-01T15:00:00")
        val lastUpdated: String
    ) {
        companion object {
            fun from(point: Point): Detail {
                return Detail(
                    userId = point.userId,
                    balance = point.amount,
                    lastUpdated = point.lastUpdated.toString()
                )
            }
        }
    }

    @Schema(description = "잔액 충전 결과 응답")
    data class ChargeResult(
        @Schema(description = "사용자 ID", example = "1")
        val userId: Long,
        
        @Schema(description = "충전 금액", example = "100000")
        val chargedAmount: BigDecimal,
        
        @Schema(description = "충전 후 잔액", example = "350000")
        val currentBalance: BigDecimal,
        
        @Schema(description = "충전 시간", example = "2024-01-01T15:00:00")
        val chargedAt: String
    ) {
        companion object {
            fun from(point: Point, chargedAmount: BigDecimal): ChargeResult {
                return ChargeResult(
                    userId = point.userId,
                    chargedAmount = chargedAmount,
                    currentBalance = point.amount,
                    chargedAt = point.lastUpdated.toString()
                )
            }
        }
    }

    @Schema(description = "포인트 이력 응답")
    data class History(
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
            fun from(history: PointHistory): History {
                return History(
                    historyId = history.historyId,
                    userId = history.userId,
                    amount = history.amount,
                    type = history.typeName,
                    description = history.description,
                    createdAt = history.createdAt.toString()
                )
            }
        }
    }
}
