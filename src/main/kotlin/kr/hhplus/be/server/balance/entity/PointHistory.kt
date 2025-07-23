package kr.hhplus.be.server.balance.entity

import jakarta.persistence.*
import kr.hhplus.be.server.balance.exception.InvalidPointAmountException
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "point_history")
data class PointHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val historyId: Long = 0,
    
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    val amount: BigDecimal,
    
    @Column(name = "type_code", nullable = false, length = 50)
    val typeCode: String,
    
    @Column(name = "description", nullable = false, length = 255)
    val description: String,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    
    // PointHistory -> User 연관관계 (N:1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    val user: kr.hhplus.be.server.user.entity.User? = null
    
    // PointHistory -> PointHistoryType 연관관계 (N:1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_code", referencedColumnName = "code", insertable = false, updatable = false)
    val historyType: PointHistoryType? = null
    
    companion object {
        fun charge(userId: Long, amount: BigDecimal, description: String = "포인트 충전"): PointHistory {
            if (amount <= BigDecimal.ZERO) {
                throw InvalidPointAmountException("충전 금액은 0보다 커야 합니다")
            }
            
            return PointHistory(
                userId = userId,
                amount = amount,
                typeCode = PointHistoryType.CHARGE,
                description = description
            )
        }
        
        fun use(userId: Long, amount: BigDecimal, description: String = "포인트 사용"): PointHistory {
            if (amount <= BigDecimal.ZERO) {
                throw InvalidPointAmountException("사용 금액은 0보다 커야 합니다")
            }

            return PointHistory(
                userId = userId,
                amount = amount,
                typeCode = PointHistoryType.USE,
                description = description
            )
        }
    }
    
    val type: String
        get() = historyType?.name ?: typeCode
}
