package kr.hhplus.be.server.payment.entity

import kr.hhplus.be.server.global.exception.ParameterValidationException
import kr.hhplus.be.server.payment.exception.PaymentAlreadyProcessedException
import jakarta.persistence.*
import kr.hhplus.be.server.reservation.entity.Reservation
import kr.hhplus.be.server.user.entity.User
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

    @Column(name = "payment_method", length = 50)
    val paymentMethod: String? = null,

    @Column(name = "status_code", nullable = false, length = 50)
    var statusCode: String,

    @Column(name = "paid_at")
    val paidAt: LocalDateTime? = null
) {

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
            paymentMethod: String = "POINT"
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
                statusCode = STATUS_PENDING,
                paymentMethod = paymentMethod
            )
        }
    }

    fun complete(): Payment {
        if (statusCode != STATUS_PENDING) {
            throw PaymentAlreadyProcessedException("이미 처리된 결제입니다: $paymentId")
        }

        return this.copy(
            statusCode = STATUS_COMPLETED,
            paidAt = LocalDateTime.now()
        )
    }

    fun fail(): Payment {
        if (statusCode != STATUS_PENDING) {
            throw PaymentAlreadyProcessedException("이미 처리된 결제입니다: $paymentId")
        }

        return this.copy(
            statusCode = STATUS_FAILED
        )
    }

    fun cancel(): Payment {
        if (statusCode == STATUS_COMPLETED) {
            throw PaymentAlreadyProcessedException("완료된 결제는 취소할 수 없습니다: $paymentId")
        }

        return this.copy(
            statusCode = STATUS_CANCELLED
        )
    }

    fun refund(): Payment {
        if (statusCode != STATUS_COMPLETED) {
            throw PaymentAlreadyProcessedException("완료된 결제만 환불 가능합니다: $paymentId")
        }

        return this.copy(
            statusCode = STATUS_REFUNDED
        )
    }

    // 상태 체크 메서드들
    fun isCompleted(): Boolean = statusCode == STATUS_COMPLETED
    fun isPending(): Boolean = statusCode == STATUS_PENDING
    fun isFailed(): Boolean = statusCode == STATUS_FAILED
    fun isCancelled(): Boolean = statusCode == STATUS_CANCELLED
    fun isRefunded(): Boolean = statusCode == STATUS_REFUNDED
}