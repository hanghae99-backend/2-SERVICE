package kr.hhplus.be.server.balance.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "point_history_type")
data class PointHistoryType(
    @Id
    @Column(name = "code", length = 50)
    val code: String,
    
    @Column(name = "name", nullable = false, length = 100)
    val name: String,
    
    @Column(name = "description", length = 255)
    val description: String? = null,
    
    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        const val CHARGE = "CHARGE"
        const val USE = "USE"
    }
}
