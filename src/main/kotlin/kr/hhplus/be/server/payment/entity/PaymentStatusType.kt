package kr.hhplus.be.server.payment.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "payment_status_type")
data class PaymentStatusType(
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
        const val PENDING = "PENDING"
        const val COMPLETED = "COMPLETED"
        const val FAILED = "FAILED"
        const val CANCELLED = "CANCELLED"
        const val REFUNDED = "REFUNDED"
    }
}
