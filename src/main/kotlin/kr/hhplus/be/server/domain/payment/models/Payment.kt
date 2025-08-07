package kr.hhplus.be.server.domain.payment.models

import kr.hhplus.be.server.global.common.BaseEntity

import kr.hhplus.be.server.global.exception.ParameterValidationException
import kr.hhplus.be.server.domain.payment.exception.PaymentAlreadyProcessedException
import jakarta.persistence.*
import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.domain.user.model.User
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "payment",
    indexes = [
        Index(name = "idx_payment_user_status", columnList = "user_id, status_code"),
        Index(name = "idx_payment_paid_at", columnList = "paid_at")
    ]
)
data class Payment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val paymentId: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    val amount: BigDecimal,

    @Column(name = "payment_method", length = 50)
    val paymentMethod: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_code", referencedColumnName = "code")
    var status: PaymentStatusType,

    @Column(name = "paid_at")
    val paidAt: LocalDateTime? = null,
    
    @Version
    @Column(name = "version") 
    var version: Long = 0
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    val user: User? = null

    @OneToMany(mappedBy = "payment", fetch = FetchType.LAZY)
    private var _reservationList: MutableList<Reservation> = mutableListOf()

    val reservationList: List<Reservation>
        get() = _reservationList.toList()

    fun addReservation(reservation: Reservation) {
        if (!_reservationList.contains(reservation)) {
            _reservationList.add(reservation)
        }
    }

    companion object {
        // 상태 코드 상수
        const val STATUS_PENDING = "PEND"
        const val STATUS_COMPLETED = "COMP"
        const val STATUS_FAILED = "FAIL"
        const val STATUS_CANCELLED = "CANC"
        const val STATUS_REFUNDED = "REFD"

        fun create(
            userId: Long,
            amount: BigDecimal,
            paymentMethod: String = "POINT",
            pendingStatus: PaymentStatusType
        ): Payment {
            if (userId <= 0) {
                throw ParameterValidationException("사용자 ID는 0보다 커야 합니다: $userId")
            }
            if (amount <= BigDecimal.ZERO) {
                throw ParameterValidationException("결제 금액은 0보다 커야 합니다: $amount")
            }

            return Payment(
                userId = userId,
                amount = amount,
                status = pendingStatus,
                paymentMethod = paymentMethod
            )
        }
    }

    fun complete(completedStatus: PaymentStatusType): Payment {
        if (status.code != STATUS_PENDING) {
            throw PaymentAlreadyProcessedException("이미 처리된 결제입니다: $paymentId")
        }

        return this.copy(
            status = completedStatus,
            paidAt = LocalDateTime.now()
        )
    }

    fun fail(failedStatus: PaymentStatusType): Payment {
        if (status.code != STATUS_PENDING) {
            throw PaymentAlreadyProcessedException("이미 처리된 결제입니다: $paymentId")
        }

        return this.copy(
            status = failedStatus
        )
    }

    fun cancel(cancelledStatus: PaymentStatusType): Payment {
        if (status.code == STATUS_COMPLETED) {
            throw PaymentAlreadyProcessedException("완료된 결제는 취소할 수 없습니다: $paymentId")
        }

        return this.copy(
            status = cancelledStatus
        )
    }

    fun refund(refundedStatus: PaymentStatusType): Payment {
        if (status.code != STATUS_COMPLETED) {
            throw PaymentAlreadyProcessedException("완료된 결제만 환불 가능합니다: $paymentId")
        }

        return this.copy(
            status = refundedStatus
        )
    }

    // 상태 체크 메서드들
    fun isCompleted(): Boolean = status.code == STATUS_COMPLETED
    fun isPending(): Boolean = status.code == STATUS_PENDING
    fun isFailed(): Boolean = status.code == STATUS_FAILED
    fun isCancelled(): Boolean = status.code == STATUS_CANCELLED
    fun isRefunded(): Boolean = status.code == STATUS_REFUNDED
}