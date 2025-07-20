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
    @Column(name = "id")
    val seatId: Long = 0,
    
    @Column(name = "concert_id", nullable = false)
    val concertId: Long,
    
    @Column(name = "seat_number", nullable = false, length = 10)
    val seatNumber: String,
    
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    val price: BigDecimal,
    
    @Column(name = "status_code", nullable = false, length = 50)
    val statusCode: String,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    
    // 연관관계 매핑 (읽기 전용)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_code", referencedColumnName = "code", insertable = false, updatable = false)
    val statusType: SeatStatusType? = null
    
    companion object {
        fun create(concertId: Long, seatNumber: String, price: BigDecimal): Seat {
            // 파라미터 검증
            if (concertId <= 0) {
                throw ParameterValidationException("콘서트 ID는 0보다 커야 합니다: $concertId")
            }
            if (seatNumber.isBlank()) {
                throw ParameterValidationException("좌석 번호는 필수입니다")
            }
            if (price <= BigDecimal.ZERO) {
                throw ParameterValidationException("좌석 가격은 0보다 커야 합니다: $price")
            }
            
            return Seat(
                concertId = concertId,
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
    
    fun isAvailable(): Boolean {
        return statusCode == SeatStatusType.AVAILABLE
    }
    
    fun isReserved(): Boolean {
        return statusCode == SeatStatusType.RESERVED
    }
    
    fun isOccupied(): Boolean {
        return statusCode == SeatStatusType.OCCUPIED
    }
    
    fun canBeReserved(): Boolean {
        return statusCode == SeatStatusType.AVAILABLE
    }
    
    // 상태 이름 조회를 위한 편의 메서드
    val status: String
        get() = statusType?.name ?: statusCode
}

// DTO for API responses
data class SeatInfo(
    val seatId: Long,
    val seatNumber: String,
    val price: BigDecimal,
    val statusCode: String,
    val status: String
) {
    companion object {
        fun from(seat: Seat): SeatInfo {
            return SeatInfo(
                seatId = seat.seatId,
                seatNumber = seat.seatNumber,
                price = seat.price,
                statusCode = seat.statusCode,
                status = seat.status
            )
        }
    }
}
