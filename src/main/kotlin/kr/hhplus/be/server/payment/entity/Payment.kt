package kr.hhplus.be.server.payment.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "payment")
data class Payment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    val paymentId: Long = 0,
    
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    
    @Column(name = "reservation_id", nullable = false)
    val reservationId: Long,
    
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    val amount: BigDecimal,
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: PaymentStatus,
    
    @Column(name = "paid_at")
    val paidAt: LocalDateTime? = null,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    
    companion object {
        fun create(userId: Long, reservationId: Long, amount: BigDecimal): Payment {
            if (amount <= BigDecimal.ZERO) {
                throw IllegalArgumentException("결제 금액은 0보다 커야 합니다")
            }
            
            return Payment(
                userId = userId,
                reservationId = reservationId,
                amount = amount,
                status = PaymentStatus.PENDING
            )
        }
    }
    
    fun complete(): Payment {
        if (status != PaymentStatus.PENDING) {
            throw PaymentAlreadyProcessedException("이미 처리된 결제입니다: $paymentId")
        }
        
        return this.copy(
            status = PaymentStatus.COMPLETED,
            paidAt = LocalDateTime.now()
        )
    }
    
    fun fail(): Payment {
        if (status != PaymentStatus.PENDING) {
            throw PaymentAlreadyProcessedException("이미 처리된 결제입니다: $paymentId")
        }
        
        return this.copy(
            status = PaymentStatus.FAILED
        )
    }
    
    fun isCompleted(): Boolean {
        return status == PaymentStatus.COMPLETED
    }
    
    fun isPending(): Boolean {
        return status == PaymentStatus.PENDING
    }
}

enum class PaymentStatus {
    PENDING, COMPLETED, FAILED
}
