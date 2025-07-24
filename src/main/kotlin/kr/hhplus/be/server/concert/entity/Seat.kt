package kr.hhplus.be.server.concert.entity

import kr.hhplus.be.server.concert.exception.InvalidSeatStatusException
import kr.hhplus.be.server.global.exception.ParameterValidationException
import kr.hhplus.be.server.reservation.entity.Reservation
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "seat")
data class Seat(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val seatId: Long = 0,
    
    @Column(name = "schedule_id", nullable = false)
    val scheduleId: Long,
    
    @Column(name = "seat_number", nullable = false, length = 10)
    val seatNumber: String,
    
    @Column(name = "seat_grade", nullable = false, length = 20)
    val seatGrade: String = "STANDARD",
    
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    val price: BigDecimal,
    
    @Column(name = "status_code", nullable = false, length = 50)
    val statusCode: String,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    
    // Seat -> ConcertSchedule 연관관계 (N:1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", insertable = false, updatable = false)
    val concertSchedule: ConcertSchedule? = null
    
    // Seat -> SeatStatusType 연관관계 (N:1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_code", referencedColumnName = "code", insertable = false, updatable = false)
    val statusType: SeatStatusType? = null
    
    companion object {
        fun create(scheduleId: Long, seatNumber: String, price: BigDecimal): Seat {
            if (scheduleId <= 0) {
                throw ParameterValidationException("스케줄 ID는 0보다 커야 합니다: $scheduleId")
            }
            if (seatNumber.isBlank()) {
                throw ParameterValidationException("좌석 번호는 필수입니다")
            }
            if (price <= BigDecimal.ZERO) {
                throw ParameterValidationException("좌석 가격은 0보다 커야 합니다: $price")
            }
            
            return Seat(
                scheduleId = scheduleId,
                seatNumber = seatNumber,
                price = price,
                statusCode = SeatStatusType.AVAILABLE
            )
        }
    }
    
    fun reserve(): Seat {
        if (statusCode != SeatStatusType.AVAILABLE) {
            throw InvalidSeatStatusException("예약 가능한 좌석이 아닙니다. 현재 상태: $statusCode")
        }
        return this.copy(
            statusCode = SeatStatusType.RESERVED,
            updatedAt = LocalDateTime.now()
        )
    }
    
    fun occupy(): Seat {
        if (statusCode != SeatStatusType.RESERVED) {
            throw InvalidSeatStatusException("임시 예약된 좌석이 아닙니다. 현재 상태: $statusCode")
        }
        return this.copy(
            statusCode = SeatStatusType.OCCUPIED,
            updatedAt = LocalDateTime.now()
        )
    }
    
    fun confirm(): Seat {
        if (statusCode != SeatStatusType.RESERVED) {
            throw InvalidSeatStatusException("임시 예약된 좌석이 아닙니다. 현재 상태: $statusCode")
        }
        return this.copy(
            statusCode = SeatStatusType.OCCUPIED,
            updatedAt = LocalDateTime.now()
        )
    }
    
    fun release(): Seat {
        if (statusCode != SeatStatusType.RESERVED) {
            throw InvalidSeatStatusException("임시 예약된 좌석이 아닙니다. 현재 상태: $statusCode")
        }
        return this.copy(
            statusCode = SeatStatusType.AVAILABLE,
            updatedAt = LocalDateTime.now()
        )
    }
    
    fun setMaintenance(): Seat {
        return this.copy(
            statusCode = SeatStatusType.MAINTENANCE,
            updatedAt = LocalDateTime.now()
        )
    }
    
    fun isAvailable(): Boolean = statusCode == SeatStatusType.AVAILABLE
    fun isReserved(): Boolean = statusCode == SeatStatusType.RESERVED
    fun isOccupied(): Boolean = statusCode == SeatStatusType.OCCUPIED
    fun canBeReserved(): Boolean = statusCode == SeatStatusType.AVAILABLE
    
    val status: String
        get() = statusType?.name ?: statusCode
}
