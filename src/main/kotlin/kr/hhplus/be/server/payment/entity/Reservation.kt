package kr.hhplus.be.server.payment.entity

import kr.hhplus.be.server.payment.entity.InvalidReservationStatusException
import kr.hhplus.be.server.global.exception.ParameterValidationException
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "reservation")
data class Reservation(
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
    
    @Column(name = "payment_id")
    val paymentId: Long? = null,
    
    @Column(name = "seat_number", nullable = false, length = 10)
    val seatNumber: String,
    
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    val price: java.math.BigDecimal,
    
    @Column(name = "status_code", nullable = false, length = 50)
    val statusCode: String,
    
    @Column(name = "reserved_at", nullable = false)
    val reservedAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "expires_at")
    val expiresAt: LocalDateTime? = null,
    
    @Column(name = "confirmed_at")
    val confirmedAt: LocalDateTime? = null,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    
    // 연관관계 매핑 (읽기 전용)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_code", referencedColumnName = "code", insertable = false, updatable = false)
    val statusType: ReservationStatusType? = null
    
    companion object {
        fun create(
            userId: Long, 
            concertId: Long,
            seatId: Long, 
            seatNumber: String,
            price: java.math.BigDecimal,
            expiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(5)
        ): Reservation {
            // 파라미터 검증
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
            if (price <= java.math.BigDecimal.ZERO) {
                throw ParameterValidationException("가격은 0보다 커야 합니다: $price")
            }
            if (expiresAt.isBefore(LocalDateTime.now())) {
                throw ParameterValidationException("만료 시간은 현재 시간 이후여야 합니다")
            }
            
            return Reservation(
                userId = userId,
                concertId = concertId,
                seatId = seatId,
                seatNumber = seatNumber,
                price = price,
                statusCode = ReservationStatusType.TEMPORARY,
                expiresAt = expiresAt
            )
        }
    }
    
    fun confirm(paymentId: Long): Reservation {
        if (statusCode != ReservationStatusType.TEMPORARY) {
            throw InvalidReservationStatusException("임시 예약 상태가 아닙니다: $reservationId")
        }
        
        return this.copy(
            statusCode = ReservationStatusType.CONFIRMED,
            paymentId = paymentId,
            confirmedAt = LocalDateTime.now()
        )
    }
    
    fun cancel(): Reservation {
        if (statusCode == ReservationStatusType.CONFIRMED) {
            throw InvalidReservationStatusException("이미 확정된 예약은 취소할 수 없습니다: $reservationId")
        }
        
        return this.copy(
            statusCode = ReservationStatusType.CANCELLED
        )
    }
    
    fun isExpired(): Boolean {
        return expiresAt?.isBefore(LocalDateTime.now()) == true
    }
    
    fun isTemporary(): Boolean {
        return statusCode == ReservationStatusType.TEMPORARY
    }
    
    fun isConfirmed(): Boolean {
        return statusCode == ReservationStatusType.CONFIRMED
    }
    
    // 상태 이름 조회를 위한 편의 메서드
    val status: String
        get() = statusType?.name ?: statusCode
}
