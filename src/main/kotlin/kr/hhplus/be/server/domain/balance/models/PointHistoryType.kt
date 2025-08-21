package kr.hhplus.be.server.domain.balance.models

import jakarta.persistence.*
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import kr.hhplus.be.server.global.common.BaseEntity

@Entity
@Table(
    name = "point_history_type",
    indexes = [
        Index(name = "idx_point_history_type_active", columnList = "is_active"),
        Index(name = "idx_point_history_type_category", columnList = "category, is_active")
    ]
)
class PointHistoryType @JsonCreator constructor(
    @JsonProperty("code")
    @Id
    @Column(name = "code", length = 50)
    var code: String,

    @JsonProperty("name")
    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @JsonProperty("description")
    @Column(name = "description", length = 255)
    var description: String? = null,

    @JsonProperty("isActive")
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
    
    @Column(name = "category", nullable = false, length = 20)
    var category: String = "GENERAL",
    
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,
    
    @Column(name = "affects_balance", nullable = false)
    var affectsBalance: Boolean = true,
    
    @Column(name = "sign", nullable = false)
    var sign: Int = 1
): BaseEntity() {
    
    companion object {
        const val CHARGE = "CHARGE"
        const val USE = "USE"
        const val REFUND = "REFUND"
        const val ADJUSTMENT = "ADJUSTMENT"
        const val BONUS = "BONUS"
        const val PENALTY = "PENALTY"
        
        const val CATEGORY_CHARGE = "CHARGE"
        const val CATEGORY_USE = "USE"
        const val CATEGORY_REFUND = "REFUND"
        const val CATEGORY_ADMIN = "ADMIN"
        
        fun createDefault(code: String, name: String, category: String, description: String? = null): PointHistoryType {
            val (sortOrder, sign, affectsBalance) = when (code) {
                CHARGE -> Triple(1, 1, true)
                USE -> Triple(2, -1, true)
                REFUND -> Triple(3, 1, true)
                BONUS -> Triple(4, 1, true)
                PENALTY -> Triple(5, -1, true)
                ADJUSTMENT -> Triple(6, 0, true)
                else -> Triple(99, 1, true)
            }
            
            return PointHistoryType(
                code = code,
                name = name,
                description = description,
                category = category,
                sortOrder = sortOrder,
                sign = sign,
                affectsBalance = affectsBalance
            )
        }
    }
    
    fun isPositive(): Boolean = sign > 0
    fun isNegative(): Boolean = sign < 0
    fun isNeutral(): Boolean = sign == 0
    
    fun isChargeType(): Boolean = category == CATEGORY_CHARGE
    fun isUseType(): Boolean = category == CATEGORY_USE
    fun isRefundType(): Boolean = category == CATEGORY_REFUND
    fun isAdminType(): Boolean = category == CATEGORY_ADMIN
    
    fun requiresApproval(): Boolean {
        return code in listOf(ADJUSTMENT, PENALTY) || category == CATEGORY_ADMIN
    }
    
    fun getDisplaySymbol(): String {
        return when {
            isPositive() -> "+"
            isNegative() -> "-"
            else -> "±"
        }
    }
    
    fun activate() {
        this.isActive = true
    }
    
    fun deactivate() {
        this.isActive = false
    }
}