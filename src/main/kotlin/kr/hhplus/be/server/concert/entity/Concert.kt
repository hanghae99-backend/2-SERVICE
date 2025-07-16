package kr.hhplus.be.server.concert.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalTime
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "concert")
data class Concert(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "concert_id")
    val concertId: Long = 0,
    
    @Column(name = "title", nullable = false)
    val title: String,
    
    @Column(name = "artist", nullable = false)
    val artist: String,
    
    @Column(name = "venue", nullable = false)
    val venue: String,
    
    @Column(name = "concert_date", nullable = false)
    val concertDate: LocalDate,
    
    @Column(name = "start_time", nullable = false)
    val startTime: LocalTime,
    
    @Column(name = "end_time", nullable = false)
    val endTime: LocalTime,
    
    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    val basePrice: BigDecimal,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

// DTO for API responses (좌석 수 포함)
data class ConcertSchedule(
    val concertId: Long,
    val title: String,
    val artist: String,
    val venue: String,
    val concertDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val basePrice: BigDecimal,
    val availableSeats: Int
)
