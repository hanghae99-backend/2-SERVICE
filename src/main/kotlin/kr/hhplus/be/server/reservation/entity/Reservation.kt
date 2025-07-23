package kr.hhplus.be.server.reservation.entity

import kr.hhplus.be.server.global.exception.ParameterValidationException
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "reservation")
class Reservation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val reservationId: Long = 0,
    
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    
    @Column(name = "concert_id", nullable = false)
    val concertId: Long,
    
    @Column(name = "seat_id", nullable = false)
    val seatId: Long,
    
    @Column(name = "payment_id", nullable = true)
    var paymentId: Long? = null,
    
    @Column(name = "seat_number", nullable = false, length = 10)
    val seatNumber: String,
    
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    val price: BigDecimal,
    
    @Column(name = "status_code", nullable = false, length = 50)
    var statusCode: String,
    
    @Column(name = "reserved_at", nullable = false)
    val reservedAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "expires_at", nullable = true)
    val expiresAt: LocalDateTime? = null,
    
    @Column(name = "confirmed_at", nullable = true)
    var confirmedAt: LocalDateTime? = null
) {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    val user: kr.hhplus.be.server.user.entity.User? = null
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", insertable = false, updatable = false)
    val concert: kr.hhplus.be.server.concert.entity.Concert? = null
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", insertable = false, updatable = false)
    val seat: kr.hhplus.be.server.concert.entity.Seat? = null
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", insertable = false, updatable = false)
    val payment: kr.hhplus.be.server.payment.entity.Payment? = null
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_code", referencedColumnName = "code", insertable = false, updatable = false)
    val statusType: ReservationStatusType? = null
    
    companion object {
        fun createTemporary(
            userId: Long,
            concertId: Long,
            seatId: Long,
            seatNumber: String,
            price: BigDecimal,
            tempMinutes: Long = 5
        ): Reservation {
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
            
            return Reservation(
                userId = userId,
                concertId = concertId,
                seatId = seatId,
                seatNumber = seatNumber,
                price = price,
                statusCode = ReservationStatusType.TEMPORARY,
                expiresAt = LocalDateTime.now().plusMinutes(tempMinutes)
            )
        }
    }
    
    fun confirm(paymentId: Long) {
        if (statusCode != ReservationStatusType.TEMPORARY) {
            throw IllegalStateException("임시 예약 상태가 아닙니다. 현재 상태: $statusCode")
        }
        if (isExpired()) {
            throw IllegalStateException("예약이 만료되었습니다")
        }
        
        this.paymentId = paymentId
        this.statusCode = ReservationStatusType.CONFIRMED
        this.confirmedAt = LocalDateTime.now()
    }
    
    fun cancel() {
        if (statusCode == ReservationStatusType.CANCELLED) {
            throw IllegalStateException("이미 취소된 예약입니다")
        }
        
        this.statusCode = ReservationStatusType.CANCELLED
    }
    
    fun isExpired(): Boolean {
        return expiresAt?.isBefore(LocalDateTime.now()) ?: false
    }
    
    fun isTemporary(): Boolean = statusCode == ReservationStatusType.TEMPORARY
    fun isConfirmed(): Boolean = statusCode == ReservationStatusType.CONFIRMED
    fun isCancelled(): Boolean = statusCode == ReservationStatusType.CANCELLED
    
    // 상태 정보 접근을 위한 속성
    val statusName: String
        get() = statusType?.name ?: statusCode
        
    val statusDescription: String
        get() = statusType?.description ?: statusCode
}
