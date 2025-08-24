package kr.hhplus.be.server.domain.reservation.models

import kr.hhplus.be.server.domain.reservation.models.ReservationStatusType
import kr.hhplus.be.server.global.common.BaseEntity
import kr.hhplus.be.server.global.exception.ParameterValidationException
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "reservation",
    indexes = [
        Index(name = "idx_reservation_user_status_date", columnList = "user_id, status_code, reserved_at"),
        Index(name = "idx_reservation_seat_status", columnList = "seat_id, status_code"),
        Index(name = "idx_reservation_expires_status", columnList = "expires_at, status_code"),
        Index(name = "idx_reservation_concert_date", columnList = "concert_id, reserved_at")
    ]
)
class Reservation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var  reservationId: Long = 0,

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "concert_id", nullable = false)
    var concertId: Long,

    @Column(name = "seat_id", nullable = false)
    var seatId: Long,

    @Column(name = "payment_id", nullable = true)
    var paymentId: Long? = null,

    @Column(name = "seat_number", nullable = false, length = 10)
    var seatNumber: String,

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    var price: BigDecimal,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_code", referencedColumnName = "code")
    var status: ReservationStatusType,

    @Column(name = "reserved_at", nullable = false)
    var reservedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "expires_at", nullable = true)
    var expiresAt: LocalDateTime? = null,

    @Column(name = "confirmed_at", nullable = true)
    var confirmedAt: LocalDateTime? = null,

) : BaseEntity() {
    
    val statusName: String
        get() = status.name
    
    val statusDescription: String
        get() = status.description ?: ""

    companion object {

        fun createTemporary(
            userId: Long,
            concertId: Long,
            seatId: Long,
            seatNumber: String,
            price: BigDecimal,
            temporaryStatus: ReservationStatusType,
            tempMinutes: Long = 5
        ): Reservation {
            validateCreateParameters(userId, concertId, seatId, seatNumber, price)

            return Reservation(
                userId = userId,
                concertId = concertId,
                seatId = seatId,
                seatNumber = seatNumber,
                price = price,
                status = temporaryStatus,
                expiresAt = LocalDateTime.now().plusMinutes(tempMinutes)
            )
        }

        private fun validateCreateParameters(
            userId: Long,
            concertId: Long,
            seatId: Long,
            seatNumber: String,
            price: BigDecimal
        ) {
            if (userId <= 0) {
                throw ParameterValidationException("사용자 ID는 0보다 커야 합니다: $userId")
            }
            if (concertId <= 0) {
                throw ParameterValidationException("콘서트 ID는 0보다 커야 합니다: $concertId")
            }
            if (seatId <= 0) {
                throw ParameterValidationException("좌석 ID는 0보다 커야 합니다: $seatId")
            }
            if (seatNumber.isBlank()) {
                throw ParameterValidationException("좌석 번호는 필수입니다")
            }
            if (price <= BigDecimal.ZERO) {
                throw ParameterValidationException("좌석 가격은 0보다 커야 합니다: $price")
            }
        }
    }

    fun confirm(paymentId: Long, confirmedStatus: ReservationStatusType) {
        validateCanConfirm()
        
        this.paymentId = paymentId
        this.status = confirmedStatus
        this.confirmedAt = LocalDateTime.now()
    }

    fun cancel(cancelledStatus: ReservationStatusType) {
        validateCanCancel()
        this.status = cancelledStatus
    }

    private fun validateCanConfirm() {
        if (status.code != ReservationStatusType.TEMPORARY) {
            throw IllegalStateException("임시 예약 상태가 아닙니다. 현재 상태: ${status.code}")
        }
        if (isExpired()) {
            throw IllegalStateException("예약이 만료되었습니다")
        }
    }

    private fun validateCanCancel() {
        if (status.code == ReservationStatusType.CANCELLED) {
            throw IllegalStateException("이미 취소된 예약입니다")
        }
    }

    fun isExpired(): Boolean {
        return expiresAt?.isBefore(LocalDateTime.now()) ?: false
    }

    fun isTemporary(): Boolean = status.code == ReservationStatusType.TEMPORARY
    fun isConfirmed(): Boolean = status.code == ReservationStatusType.CONFIRMED
    fun isCancelled(): Boolean = status.code == ReservationStatusType.CANCELLED
}
