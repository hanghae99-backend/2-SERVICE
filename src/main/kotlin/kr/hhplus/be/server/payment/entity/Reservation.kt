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
    @Column(name = "reservation_id")
    val reservationId: Long = 0,
    
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    
    @Column(name = "seat_id", nullable = false)
    val seatId: Long,
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: ReservationStatus,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "expires_at")
    val expiresAt: LocalDateTime? = null,
    
    @Column(name = "confirmed_at")
    val confirmedAt: LocalDateTime? = null
) {
    
    companion object {
        fun create(userId: Long, seatId: Long, expiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(5)): Reservation {
            // 파라미터 검증
            if (userId <= 0) {
                throw ParameterValidationException("사용자 ID는 0보다 커야 합니다: $userId")
            }
            if (seatId <= 0) {
                throw ParameterValidationException("좌석 ID는 0보다 커야 합니다: $seatId")
            }
            if (expiresAt.isBefore(LocalDateTime.now())) {
                throw ParameterValidationException("만료 시간은 현재 시간 이후여야 합니다")
            }
            
            return Reservation(
                userId = userId,
                seatId = seatId,
                status = ReservationStatus.TEMPORARY,
                expiresAt = expiresAt
            )
        }
    }
    
    fun confirm(): Reservation {
        if (status != ReservationStatus.TEMPORARY) {
            throw InvalidReservationStatusException("임시 예약 상태가 아닙니다: $reservationId")
        }
        
        return this.copy(
            status = ReservationStatus.CONFIRMED,
            confirmedAt = LocalDateTime.now()
        )
    }
    
    fun cancel(): Reservation {
        if (status == ReservationStatus.CONFIRMED) {
            throw InvalidReservationStatusException("이미 확정된 예약은 취소할 수 없습니다: $reservationId")
        }
        
        return this.copy(
            status = ReservationStatus.CANCELLED
        )
    }
    
    fun isExpired(): Boolean {
        return expiresAt?.isBefore(LocalDateTime.now()) == true
    }
    
    fun isTemporary(): Boolean {
        return status == ReservationStatus.TEMPORARY
    }
    
    fun isConfirmed(): Boolean {
        return status == ReservationStatus.CONFIRMED
    }
}

enum class ReservationStatus {
    TEMPORARY, CONFIRMED, CANCELLED
}
