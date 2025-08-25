package kr.hhplus.be.server.domain.balance.models

import kr.hhplus.be.server.global.common.BaseEntity
import jakarta.persistence.*
import kr.hhplus.be.server.domain.balance.exception.InvalidAmountException
import kr.hhplus.be.server.domain.balance.rules.BalanceBusinessRules
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "point_history",
    indexes = [
        Index(name = "idx_point_history_user_id_created_at", columnList = "user_id, created_at"),
        Index(name = "idx_point_history_type_code_created_at", columnList = "type_code, created_at"),
        Index(name = "idx_point_history_user_created_desc", columnList = "user_id, created_at DESC"),
        Index(name = "idx_point_history_date_type", columnList = "created_at, type_code, amount")
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
    var description: String,
    
    @Column(name = "balance_after", nullable = true, precision = 10, scale = 2)
    var balanceAfter: BigDecimal? = null,
    
    @Column(name = "related_entity_type", nullable = true, length = 50)
    var relatedEntityType: String? = null,
    
    @Column(name = "related_entity_id", nullable = true)
    var relatedEntityId: Long? = null
) : BaseEntity() {
    
    companion object {
        
        fun charge(
            userId: Long, 
            amount: BigDecimal, 
            chargeType: PointHistoryType, 
            description: String = "포인트 충전",
            balanceAfter: BigDecimal? = null
        ): PointHistory {
            validateAmount(amount, "충전")
            
            return PointHistory(
                userId = userId,
                amount = amount,
                historyType = chargeType,
                description = description,
                balanceAfter = balanceAfter
            )
        }
        
        fun use(
            userId: Long, 
            amount: BigDecimal, 
            useType: PointHistoryType, 
            description: String = "포인트 사용",
            balanceAfter: BigDecimal? = null,
            relatedEntityType: String? = null,
            relatedEntityId: Long? = null
        ): PointHistory {
            validateAmount(amount, "사용")
            
            return PointHistory(
                userId = userId,
                amount = amount,
                historyType = useType,
                description = description,
                balanceAfter = balanceAfter,
                relatedEntityType = relatedEntityType,
                relatedEntityId = relatedEntityId
            )
        }
        
        fun refund(
            userId: Long,
            amount: BigDecimal,
            refundType: PointHistoryType,
            description: String = "포인트 환불",
            balanceAfter: BigDecimal? = null,
            relatedEntityType: String? = null,
            relatedEntityId: Long? = null
        ): PointHistory {
            validateAmount(amount, "환불")
            
            return PointHistory(
                userId = userId,
                amount = amount,
                historyType = refundType,
                description = description,
                balanceAfter = balanceAfter,
                relatedEntityType = relatedEntityType,
                relatedEntityId = relatedEntityId
            )
        }
        
        fun adjustment(
            userId: Long,
            amount: BigDecimal,
            adjustmentType: PointHistoryType,
            description: String,
            balanceAfter: BigDecimal? = null
        ): PointHistory {
            if (description.isBlank()) {
                throw IllegalArgumentException("조정 사유는 필수입니다")
            }
            
            return PointHistory(
                userId = userId,
                amount = amount,
                historyType = adjustmentType,
                description = description,
                balanceAfter = balanceAfter,
                relatedEntityType = "ADMIN_ADJUSTMENT"
            )
        }
        
        private fun validateAmount(amount: BigDecimal, operation: String) {
            if (amount <= BigDecimal.ZERO) {
                throw InvalidAmountException(amount)
            }
        }
    }
    
    val typeName: String
        get() = historyType.name
    
    val typeCode: String
        get() = historyType.code
    
    fun isCharge(): Boolean = historyType.code == PointHistoryType.CHARGE
    fun isUse(): Boolean = historyType.code == PointHistoryType.USE
    fun isRefund(): Boolean = historyType.code == PointHistoryType.REFUND
    fun isAdjustment(): Boolean = historyType.code == PointHistoryType.ADJUSTMENT
    
    fun isRelatedTo(entityType: String, entityId: Long): Boolean {
        return relatedEntityType == entityType && relatedEntityId == entityId
    }
    
    fun getTransactionDate(): LocalDate {
        return createdAt?.toLocalDate() ?: LocalDate.now()
    }
    
    fun isToday(): Boolean {
        return getTransactionDate() == LocalDate.now()
    }
    
    fun isInDateRange(startDate: LocalDate, endDate: LocalDate): Boolean {
        val transactionDate = getTransactionDate()
        return !transactionDate.isBefore(startDate) && !transactionDate.isAfter(endDate)
    }
    
    fun getDisplayAmount(): String {
        return when {
            isCharge() || isRefund() -> "+${amount}"
            isUse() -> "-${amount}"
            else -> amount.toString()
        }
    }
}
