package kr.hhplus.be.server.domain.payment.models

import kr.hhplus.be.server.domain.payment.exception.PaymentAlreadyProcessedException
import kr.hhplus.be.server.global.common.BaseEntity
import kr.hhplus.be.server.global.exception.ParameterValidationException
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

    fun complete() {
        if (status.code != PaymentStatusType.PENDING) {
            throw PaymentAlreadyProcessedException(paymentId, status.code)
        }
        // 상태 변경은 서비스 레이어에서 처리
        this.paidAt = LocalDateTime.now()
    }

    fun updateStatus(newStatus: PaymentStatusType) {
        this.status = newStatus
    }

    fun fail() {
        if (status.code != PaymentStatusType.PENDING) {
            throw PaymentAlreadyProcessedException(paymentId, status.code)
        }
        // 상태 변경은 서비스 레이어에서 처리
    }

    fun cancel() {
        if (status.code == PaymentStatusType.COMPLETED) {
            throw PaymentAlreadyProcessedException(paymentId, status.code)
        }
        // 상태 변경은 서비스 레이어에서 처리
    }

    fun refund() {
        if (status.code != PaymentStatusType.COMPLETED) {
            throw PaymentAlreadyProcessedException(paymentId, status.code)
        }
        // 상태 변경은 서비스 레이어에서 처리
    }

    fun isCompleted(): Boolean = status.code == PaymentStatusType.COMPLETED
    fun isPending(): Boolean = status.code == PaymentStatusType.PENDING
    fun isFailed(): Boolean = status.code == PaymentStatusType.FAILED
    fun isCancelled(): Boolean = status.code == PaymentStatusType.CANCELLED
    fun isRefunded(): Boolean = status.code == PaymentStatusType.REFUNDED
}
