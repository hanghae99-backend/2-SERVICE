package kr.hhplus.be.server.domain.reservation.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonTypeInfo
import jakarta.persistence.*
import com.fasterxml.jackson.annotation.JsonProperty
import kr.hhplus.be.server.global.common.BaseEntity

@Entity
@Table(
    name = "reservation_status_type",
    indexes = [
        Index(name = "idx_reservation_status_type_active", columnList = "is_active"),
        Index(name = "idx_reservation_status_type_category", columnList = "category, is_active")
    ]
)
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
class ReservationStatusType @JsonCreator constructor(
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
    
    @JsonProperty("category")
    @Column(name = "category", nullable = false, length = 20)
    var category: String = "NORMAL",
    
    @JsonProperty("sortOrder")
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,
    
    @JsonProperty("isFinal")
    @Column(name = "is_final", nullable = false)
    var isFinal: Boolean = false,
    
    @JsonProperty("autoExpireMinutes")
    @Column(name = "auto_expire_minutes", nullable = true)
    var autoExpireMinutes: Int? = null
): BaseEntity() {
    
    companion object {
        const val TEMPORARY = "TEMPORARY"
        const val CONFIRMED = "CONFIRMED"
        const val CANCELLED = "CANCELLED"
        const val EXPIRED = "EXPIRED"
        const val COMPLETED = "COMPLETED"
        
        const val CATEGORY_NORMAL = "NORMAL"
        const val CATEGORY_CANCELLED = "CANCELLED"
        const val CATEGORY_EXPIRED = "EXPIRED"
        
        fun createDefault(code: String, name: String, category: String, description: String? = null): ReservationStatusType {
            val (sortOrder, isFinal, autoExpireMinutes) = when (code) {
                TEMPORARY -> Triple(1, false, 5)
                CONFIRMED -> Triple(2, false, null)
                COMPLETED -> Triple(3, true, null)
                CANCELLED -> Triple(4, true, null)
                EXPIRED -> Triple(5, true, null)
                else -> Triple(99, false, null)
            }
            
            return ReservationStatusType(
                code = code,
                name = name,
                description = description,
                category = category,
                sortOrder = sortOrder,
                isFinal = isFinal,
                autoExpireMinutes = autoExpireMinutes
            )
        }
    }
    
    fun isActiveStatus(): Boolean {
        return code in listOf(TEMPORARY, CONFIRMED) && isActive
    }
    
    fun isTemporary(): Boolean {
        return code == TEMPORARY
    }
    
    fun isConfirmed(): Boolean {
        return code == CONFIRMED
    }
    
    fun isCancelled(): Boolean {
        return code == CANCELLED
    }
    
    fun isExpired(): Boolean {
        return code == EXPIRED
    }
    
    fun isCompleted(): Boolean {
        return code == COMPLETED
    }
    
    fun canTransitionTo(targetStatus: ReservationStatusType): Boolean {
        if (isFinal) return false
        
        return when (code) {
            TEMPORARY -> targetStatus.code in listOf(CONFIRMED, CANCELLED, EXPIRED)
            CONFIRMED -> targetStatus.code in listOf(COMPLETED, CANCELLED)
            else -> false
        }
    }
    
    fun requiresPayment(): Boolean {
        return code == TEMPORARY
    }
    
    fun allowsModification(): Boolean {
        return code in listOf(TEMPORARY, CONFIRMED) && !isFinal
    }
    
    fun getStatusColor(): String {
        return when (code) {
            TEMPORARY -> "#ffc107"
            CONFIRMED -> "#17a2b8"
            COMPLETED -> "#28a745"
            CANCELLED -> "#6c757d"
            EXPIRED -> "#dc3545"
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