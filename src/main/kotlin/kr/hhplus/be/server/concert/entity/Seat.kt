package kr.hhplus.be.server.concert.entity

import kr.hhplus.be.server.concert.entity.InvalidSeatStatusException
import kr.hhplus.be.server.global.exception.ParameterValidationException
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "seat")
data class Seat(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    val seatId: Long = 0,
    
    @Column(name = "concert_id", nullable = false)
    val concertId: Long,
    
    @Column(name = "seat_number", nullable = false)
    val seatNumber: Int,
    
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    val price: BigDecimal,
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: SeatStatus,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    
    companion object {
        fun create(concertId: Long, seatNumber: Int, price: BigDecimal): Seat {
            // 파라미터 검증
            if (concertId <= 0) {
                throw ParameterValidationException("콘서트 ID는 0보다 커야 합니다: $concertId")
            }
            if (seatNumber <= 0) {
                throw ParameterValidationException("좌석 번호는 0보다 커야 합니다: $seatNumber")
            }
            if (price <= BigDecimal.ZERO) {
                throw ParameterValidationException("좌석 가격은 0보다 커야 합니다: $price")
            }
            
            return Seat(
                concertId = concertId,
                seatNumber = seatNumber,
                price = price,
                status = SeatStatus.AVAILABLE
            )
        }
    }
    
    fun reserve(): Seat {
        if (status != SeatStatus.AVAILABLE) {
            throw InvalidSeatStatusException("예약 가능한 좌석이 아닙니다. 현재 상태: $status")
        }
        
        return this.copy(
            status = SeatStatus.RESERVED,
            updatedAt = LocalDateTime.now()
        )
    }
    
    fun confirm(): Seat {
        if (status != SeatStatus.RESERVED) {
            throw InvalidSeatStatusException("임시 예약된 좌석이 아닙니다. 현재 상태: $status")
        }
        
        return this.copy(
            status = SeatStatus.CONFIRMED,
            updatedAt = LocalDateTime.now()
        )
    }
    
    fun release(): Seat {
        if (status != SeatStatus.RESERVED) {
            throw InvalidSeatStatusException("임시 예약된 좌석이 아닙니다. 현재 상태: $status")
        }
        
        return this.copy(
            status = SeatStatus.AVAILABLE,
            updatedAt = LocalDateTime.now()
        )
    }
    
    fun makeUnavailable(): Seat {
        return this.copy(
            status = SeatStatus.UNAVAILABLE,
            updatedAt = LocalDateTime.now()
        )
    }
    
    fun isAvailable(): Boolean {
        return status == SeatStatus.AVAILABLE
    }
    
    fun isReserved(): Boolean {
        return status == SeatStatus.RESERVED
    }
    
    fun isConfirmed(): Boolean {
        return status == SeatStatus.CONFIRMED
    }
    
    fun canBeReserved(): Boolean {
        return status == SeatStatus.AVAILABLE
    }
}

enum class SeatStatus {
    AVAILABLE,    // 예약 가능
    RESERVED,     // 임시 예약됨 (5분 제한)
    CONFIRMED,    // 예약 확정됨
    UNAVAILABLE   // 예약 불가
}

// DTO for API responses
data class SeatInfo(
    val seatId: Long,
    val seatNumber: Int,
    val price: BigDecimal,
    val status: SeatStatus
)
