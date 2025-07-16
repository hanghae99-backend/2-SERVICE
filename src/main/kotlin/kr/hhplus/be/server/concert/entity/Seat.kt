package kr.hhplus.be.server.concert.entity

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
)

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
