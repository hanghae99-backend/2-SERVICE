package kr.hhplus.be.server.domain.payment.models

import jakarta.persistence.*
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import kr.hhplus.be.server.global.common.BaseEntity

@Entity
@Table(
    name = "payment_status_type",
    indexes = [
        Index(name = "idx_payment_status_type_active", columnList = "is_active"),
        Index(name = "idx_payment_status_type_category", columnList = "category, is_active")
    ]
)
class PaymentStatusType @JsonCreator constructor(
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
    var category: String = "NORMAL",
    
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,
    
    @Column(name = "is_final", nullable = false)
    var isFinal: Boolean = false,
    
    @Column(name = "requires_action", nullable = false)
    var requiresAction: Boolean = false
): BaseEntity() {
    
    companion object {
        const val PENDING = "PEND"
        const val PROCESSING = "PROC"
        const val COMPLETED = "COMP"
        const val FAILED = "FAIL"
        const val CANCELLED = "CANC"
        const val REFUNDED = "REFD"
        const val EXPIRED = "EXPR"
        
        const val CATEGORY_NORMAL = "NORMAL"
        const val CATEGORY_REFUND = "REFUND"
        const val CATEGORY_CANCEL = "CANCEL"
        const val CATEGORY_ERROR = "ERROR"
        
        fun createDefault(code: String, name: String, category: String, description: String? = null): PaymentStatusType {
            val (sortOrder, isFinal, requiresAction) = when (code) {
                PENDING -> Triple(1, false, true)
                PROCESSING -> Triple(2, false, false)
                COMPLETED -> Triple(3, true, false)
                FAILED -> Triple(4, true, false)
                CANCELLED -> Triple(5, true, false)
                REFUNDED -> Triple(6, true, false)
                EXPIRED -> Triple(7, true, false)
                else -> Triple(99, false, false)
            }
            
            return PaymentStatusType(
                code = code,
                name = name,
                description = description,
                category = category,
                sortOrder = sortOrder,
                isFinal = isFinal,
                requiresAction = requiresAction
            )
        }
    }
    
    fun isProcessable(): Boolean {
        return code == PENDING && isActive
    }
    
    fun isSuccessful(): Boolean {
        return code == COMPLETED
    }
    
    fun isFailureState(): Boolean {
        return code in listOf(FAILED, CANCELLED, EXPIRED)
    }
    
    fun canTransitionTo(targetStatus: PaymentStatusType): Boolean {
        if (isFinal) return false
        
        return when (code) {
            PENDING -> targetStatus.code in listOf(PROCESSING, COMPLETED, FAILED, CANCELLED, EXPIRED)
            PROCESSING -> targetStatus.code in listOf(COMPLETED, FAILED, CANCELLED)
            COMPLETED -> targetStatus.code in listOf(REFUNDED, CANCELLED)
            else -> false
        }
    }
    
    fun requiresManualIntervention(): Boolean {
        return code in listOf(FAILED, EXPIRED) || (category == CATEGORY_ERROR && requiresAction)
    }
    
    fun isRefundable(): Boolean {
        return code == COMPLETED && category == CATEGORY_NORMAL
    }
    
    fun isCancellable(): Boolean {
        return code in listOf(PENDING, PROCESSING) && !isFinal
    }
    
    fun getStatusColor(): String {
        return when (code) {
            PENDING -> "#ffc107"
            PROCESSING -> "#17a2b8"
            COMPLETED -> "#28a745"
            FAILED -> "#dc3545"
            CANCELLED -> "#6c757d"
            REFUNDED -> "#fd7e14"
            EXPIRED -> "#e83e8c"
            else -> "#6c757d"
        }
    }
    
    fun activate() {
        this.isActive = true
    }
    
    fun deactivate() {
        this.isActive = false
    }
}