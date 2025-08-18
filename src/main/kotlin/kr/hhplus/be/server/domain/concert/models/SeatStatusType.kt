package kr.hhplus.be.server.domain.concert.models

import jakarta.persistence.*
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import kr.hhplus.be.server.global.common.BaseEntity

@Entity
@Table(
    name = "seat_status_type",
    indexes = [
        Index(name = "idx_seat_status_type_active", columnList = "is_active"),
        Index(name = "idx_seat_status_type_name", columnList = "name")
    ]
)
class SeatStatusType @JsonCreator constructor(
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
    
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,
    
    @Column(name = "color_code", length = 7)
    var colorCode: String? = null
): BaseEntity() {
    
    companion object {
        const val AVAILABLE = "AVAILABLE"
        const val RESERVED = "RESERVED"
        const val OCCUPIED = "OCCUPIED"
        const val MAINTENANCE = "MAINTENANCE"
        const val BLOCKED = "BLOCKED"
        
        fun createDefault(code: String, name: String, description: String? = null): SeatStatusType {
            val sortOrder = when (code) {
                AVAILABLE -> 1
                RESERVED -> 2
                OCCUPIED -> 3
                MAINTENANCE -> 4
                BLOCKED -> 5
                else -> 99
            }
            
            val colorCode = when (code) {
                AVAILABLE -> "#28a745"
                RESERVED -> "#ffc107"
                OCCUPIED -> "#dc3545"
                MAINTENANCE -> "#6c757d"
                BLOCKED -> "#343a40"
                else -> null
            }
            
            return SeatStatusType(
                code = code,
                name = name,
                description = description,
                sortOrder = sortOrder,
                colorCode = colorCode
            )
        }
    }
    
    fun isBookable(): Boolean {
        return code == AVAILABLE && isActive
    }
    
    fun isUnavailable(): Boolean {
        return code in listOf(OCCUPIED, MAINTENANCE, BLOCKED) || !isActive
    }
    
    fun canTransitionTo(targetStatus: SeatStatusType): Boolean {
        return when (code) {
            AVAILABLE -> targetStatus.code in listOf(RESERVED, MAINTENANCE, BLOCKED)
            RESERVED -> targetStatus.code in listOf(OCCUPIED, AVAILABLE, MAINTENANCE)
            OCCUPIED -> targetStatus.code in listOf(AVAILABLE, MAINTENANCE)
            MAINTENANCE -> targetStatus.code in listOf(AVAILABLE, BLOCKED)
            BLOCKED -> targetStatus.code in listOf(AVAILABLE, MAINTENANCE)
            else -> false
        }
    }
    
    fun activate() {
        this.isActive = true
    }
    
    fun deactivate() {
        this.isActive = false
    }
}
