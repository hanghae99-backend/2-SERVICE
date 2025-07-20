package kr.hhplus.be.server.payment.entity

import kr.hhplus.be.server.global.exception.ParameterValidationException
import kr.hhplus.be.server.payment.entity.PaymentAlreadyProcessedException
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "payment")
data class Payment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val paymentId: Long = 0,
    
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    val amount: BigDecimal,
    
    @Column(name = "status_code", nullable = false, length = 50)
    val statusCode: String,
    
    @Column(name = "payment_method", length = 50)
    val paymentMethod: String? = null,
    
    @Column(name = "paid_at")
    val paidAt: LocalDateTime? = null
) {
    
    // 연관관계 매핑 (읽기 전용)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_code", referencedColumnName = "code", insertable = false, updatable = false)
    val statusType: PaymentStatusType? = null
    
    companion object {
        fun create(
            userId: Long, 
            amount: BigDecimal, 
            paymentMethod: String = "POINT"
        ): Payment {
            // 파라미터 검증
            if (userId <= 0) {
                throw ParameterValidationException("사용자 ID는 0보다 커야 합니다: $userId")
            }
            if (amount <= BigDecimal.ZERO) {
                throw ParameterValidationException("결제 금액은 0보다 커야 합니다: $amount")
            }
            
            return Payment(
                userId = userId,
                amount = amount,
                statusCode = PaymentStatusType.PENDING,
                paymentMethod = paymentMethod
            )
        }
    }
    
    fun complete(): Payment {
        if (statusCode != PaymentStatusType.PENDING) {
            throw PaymentAlreadyProcessedException("이미 처리된 결제입니다: $paymentId")
        }
        
        return this.copy(
            statusCode = PaymentStatusType.COMPLETED,
            paidAt = LocalDateTime.now()
        )
    }
    
    fun fail(): Payment {
        if (statusCode != PaymentStatusType.PENDING) {
            throw PaymentAlreadyProcessedException("이미 처리된 결제입니다: $paymentId")
        }
        
        return this.copy(
            statusCode = PaymentStatusType.FAILED
        )
    }
    
    fun cancel(): Payment {
        if (statusCode == PaymentStatusType.COMPLETED) {
            throw PaymentAlreadyProcessedException("완료된 결제는 취소할 수 없습니다: $paymentId")
        }
        
        return this.copy(
            statusCode = PaymentStatusType.CANCELLED
        )
    }
    
    fun refund(): Payment {
        if (statusCode != PaymentStatusType.COMPLETED) {
            throw PaymentAlreadyProcessedException("완료된 결제만 환불 가능합니다: $paymentId")
        }
        
        return this.copy(
            statusCode = PaymentStatusType.REFUNDED
        )
    }
    
    fun isCompleted(): Boolean {
        return statusCode == PaymentStatusType.COMPLETED
    }
    
    fun isPending(): Boolean {
        return statusCode == PaymentStatusType.PENDING
    }
    
    // 상태 이름 조회를 위한 편의 메서드
    val status: String
        get() = statusType?.name ?: statusCode
}
