package kr.hhplus.be.server.domain.balance.models

import kr.hhplus.be.server.global.common.BaseEntity

import jakarta.persistence.*
import kr.hhplus.be.server.domain.balance.exception.InvalidPointAmountException
import kr.hhplus.be.server.domain.user.model.User
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "point_history",
    indexes = [
        Index(name = "idx_point_history_user_id_created_at", columnList = "user_id, created_at"),
        Index(name = "idx_point_history_type_code_created_at", columnList = "type_code, created_at"),
        Index(name = "idx_point_history_user_created_desc", columnList = "user_id, created_at DESC")
    ]
)
class PointHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var historyId: Long = 0,
    
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    var amount: BigDecimal,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_code", referencedColumnName = "code")
    var historyType: PointHistoryType,
    
    @Column(name = "description", nullable = false, length = 255)
    var description: String
) : BaseEntity() {
    
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
