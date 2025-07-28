package kr.hhplus.be.server.domain.balance.models

import jakarta.persistence.*
import kr.hhplus.be.server.domain.balance.exception.InvalidPointAmountException
import kr.hhplus.be.server.domain.user.model.User
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
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_code", referencedColumnName = "code")
    val historyType: PointHistoryType,
    
    @Column(name = "description", nullable = false, length = 255)
    val description: String,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    
    // PointHistory -> User 연관관계 (N:1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    val user: User? = null
    
    companion object {
        // 타입 코드 상수
        const val TYPE_CHARGE = "CHARGE"
        const val TYPE_USE = "USE"
        
        fun charge(userId: Long, amount: BigDecimal, chargeType: PointHistoryType, description: String = "포인트 충전"): PointHistory {
            if (amount <= BigDecimal.ZERO) {
                throw InvalidPointAmountException("충전 금액은 0보다 커야 합니다")
            }
            
            return PointHistory(
                userId = userId,
                amount = amount,
                historyType = chargeType,
                description = description
            )
        }
        
        fun use(userId: Long, amount: BigDecimal, useType: PointHistoryType, description: String = "포인트 사용"): PointHistory {
            if (amount <= BigDecimal.ZERO) {
                throw InvalidPointAmountException("사용 금액은 0보다 커야 합니다")
            }

            return PointHistory(
                userId = userId,
                amount = amount,
                historyType = useType,
                description = description
            )
        }
    }
    
    val typeName: String
        get() = historyType.name
}
