package kr.hhplus.be.server.domain.payment.models

import kr.hhplus.be.server.global.common.BaseEntity
import kr.hhplus.be.server.global.exception.ParameterValidationException
import kr.hhplus.be.server.domain.payment.exception.PaymentAlreadyProcessedException
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "payment",
    indexes = [
        Index(name = "idx_payment_user_status", columnList = "user_id, status_code"),
        Index(name = "idx_payment_paid_at", columnList = "paid_at"),
        Index(name = "idx_payment_reservation", columnList = "reservation_id")
    ]
)
class Payment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var paymentId: Long = 0,

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "reservation_id", nullable = true)
    var reservationId: Long? = null,

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    var amount: BigDecimal,

    @Column(name = "payment_method", length = 50)
    var paymentMethod: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_code", referencedColumnName = "code")
    var status: PaymentStatusType,

    @Column(name = "paid_at")
    var paidAt: LocalDateTime? = null,
) : BaseEntity() {

    companion object {
        // 상태 코드 상수
        const val STATUS_PENDING = "PEND"
        const val STATUS_COMPLETED = "COMP"
        const val STATUS_FAILED = "FAIL"
        const val STATUS_CANCELLED = "CANC"
        const val STATUS_REFUNDED = "REFD"

        fun createForReservation(
            userId: Long,
            reservationId: Long,
            amount: BigDecimal,
            paymentMethod: String = "POINT",
            pendingStatus: PaymentStatusType
        ): Payment {
            if (userId <= 0) throw ParameterValidationException("사용자 ID는 0보다 커야 합니다: $userId")
            if (reservationId <= 0) throw ParameterValidationException("예약 ID는 0보다 커야 합니다: $reservationId")
            if (amount <= BigDecimal.ZERO) throw ParameterValidationException("결제 금액은 0보다 커야 합니다: $amount")

            return Payment(
                userId = userId,
                reservationId = reservationId,
                amount = amount,
                paymentMethod = paymentMethod,
                status = pendingStatus
            )
        }

        fun create(
            userId: Long,
            amount: BigDecimal,
            paymentMethod: String = "POINT",
            pendingStatus: PaymentStatusType
        ): Payment {
            if (userId <= 0) throw ParameterValidationException("사용자 ID는 0보다 커야 합니다: $userId")
            if (amount <= BigDecimal.ZERO) throw ParameterValidationException("결제 금액은 0보다 커야 합니다: $amount")

            return Payment(
                userId = userId,
                reservationId = null,
                amount = amount,
                paymentMethod = paymentMethod,
                status = pendingStatus
            )
        }
    }

    // copy 함수 직접 구현 (paymentId 제외)
    fun copy(
        userId: Long = this.userId,
        reservationId: Long? = this.reservationId,
        amount: BigDecimal = this.amount,
        paymentMethod: String? = this.paymentMethod,
        status: PaymentStatusType = this.status,
        paidAt: LocalDateTime? = this.paidAt
    ): Payment {
        return Payment(
            userId = userId,
            reservationId = reservationId,
            amount = amount,
            paymentMethod = paymentMethod,
            status = status,
            paidAt = paidAt
        )
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
        return this.copy(status = failedStatus)
    }

    fun cancel(cancelledStatus: PaymentStatusType): Payment {
        if (status.code == STATUS_COMPLETED) {
            throw PaymentAlreadyProcessedException("완료된 결제는 취소할 수 없습니다: $paymentId")
        }
        return this.copy(status = cancelledStatus)
    }

    fun refund(refundedStatus: PaymentStatusType): Payment {
        if (status.code != STATUS_COMPLETED) {
            throw PaymentAlreadyProcessedException("완료된 결제만 환불 가능합니다: $paymentId")
        }
        return this.copy(status = refundedStatus)
    }

    // 상태 체크
    fun isCompleted(): Boolean = status.code == STATUS_COMPLETED
    fun isPending(): Boolean = status.code == STATUS_PENDING
    fun isFailed(): Boolean = status.code == STATUS_FAILED
    fun isCancelled(): Boolean = status.code == STATUS_CANCELLED
    fun isRefunded(): Boolean = status.code == STATUS_REFUNDED
}
