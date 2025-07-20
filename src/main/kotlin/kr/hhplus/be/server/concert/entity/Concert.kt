package kr.hhplus.be.server.concert.entity

import kr.hhplus.be.server.global.exception.ParameterValidationException
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
) {
    
    companion object {
        fun create(
            title: String,
            artist: String,
            venue: String,
            concertDate: LocalDate,
            startTime: LocalTime,
            endTime: LocalTime,
            basePrice: BigDecimal
        ): Concert {
            // 파라미터 검증
            if (title.isBlank()) {
                throw ParameterValidationException("콘서트 제목은 필수입니다")
            }
            if (artist.isBlank()) {
                throw ParameterValidationException("아티스트 이름은 필수입니다")
            }
            if (venue.isBlank()) {
                throw ParameterValidationException("공연장 이름은 필수입니다")
            }
            if (concertDate.isBefore(LocalDate.now())) {
                throw ParameterValidationException("콘서트 날짜는 현재 날짜 이후여야 합니다")
            }
            if (endTime.isBefore(startTime) || endTime == startTime) {
                throw ParameterValidationException("종료 시간은 시작 시간보다 늦어야 합니다")
            }
            if (basePrice <= BigDecimal.ZERO) {
                throw ParameterValidationException("기본 가격은 0보다 커야 합니다")
            }
            
            return Concert(
                title = title,
                artist = artist,
                venue = venue,
                concertDate = concertDate,
                startTime = startTime,
                endTime = endTime,
                basePrice = basePrice
            )
        }
    }
    
    fun isBookingAvailable(): Boolean {
        // 콘서트 날짜가 지나지 않았는지 확인
        return concertDate.isAfter(LocalDate.now()) || 
               (concertDate.isEqual(LocalDate.now()) && startTime.isAfter(LocalTime.now()))
    }
    
    fun isInProgress(): Boolean {
        val now = LocalDateTime.now()
        val concertStart = concertDate.atTime(startTime)
        val concertEnd = concertDate.atTime(endTime)
        
        return now.isAfter(concertStart) && now.isBefore(concertEnd)
    }
    
    fun isFinished(): Boolean {
        val now = LocalDateTime.now()
        val concertEnd = concertDate.atTime(endTime)
        
        return now.isAfter(concertEnd)
    }
    
    fun getDurationInMinutes(): Long {
        return java.time.Duration.between(startTime, endTime).toMinutes()
    }
}

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
