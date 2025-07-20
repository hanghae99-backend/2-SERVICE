package kr.hhplus.be.server.balance.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "point_history")
data class PointHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    val historyId: Long = 0,
    
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    val amount: BigDecimal,
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    val type: PointHistoryType,
    
    @Column(name = "description", nullable = false)
    val description: String,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    
    companion object {
        fun charge(userId: Long, amount: BigDecimal, description: String = "포인트 충전"): PointHistory {
            if (amount <= BigDecimal.ZERO) {
                throw InvalidPointAmountException("충전 금액은 0보다 커야 합니다")
            }
            
            return PointHistory(
                userId = userId,
                amount = amount,
                type = PointHistoryType.CHARGE,
                description = description
            )
        }
        
        fun usage(userId: Long, amount: BigDecimal, description: String = "포인트 사용"): PointHistory {
            if (amount <= BigDecimal.ZERO) {
                throw InvalidPointAmountException("사용 금액은 0보다 커야 합니다")
            }
            
            return PointHistory(
                userId = userId,
                amount = amount,
                type = PointHistoryType.USAGE,
                description = description
            )
        }
    }
}

enum class PointHistoryType {
    CHARGE, USAGE
}
