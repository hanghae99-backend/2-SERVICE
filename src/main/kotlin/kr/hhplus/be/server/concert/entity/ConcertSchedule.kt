package kr.hhplus.be.server.concert.entity

import kr.hhplus.be.server.global.exception.ParameterValidationException
import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime

@Entity
@Table(name = "concert_schedule")
data class ConcertSchedule(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val scheduleId: Long = 0,
    
    @Column(name = "concert_id", nullable = false)
    val concertId: Long,
    
    @Column(name = "concert_date", nullable = false)
    val concertDate: LocalDate,
    
    @Column(name = "venue", nullable = false, length = 200)
    val venue: String,
    
    @Column(name = "total_seats", nullable = false)
    val totalSeats: Int,
    
    @Column(name = "available_seats", nullable = false)
    val availableSeats: Int,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    
    // 연관관계 매핑 (읽기 전용)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", insertable = false, updatable = false)
    val concert: Concert? = null
    
    companion object {
        fun create(
            concertId: Long,
            concertDate: LocalDate,
            venue: String,
            totalSeats: Int
        ): ConcertSchedule {
            // 파라미터 검증
            if (concertId <= 0) {
                throw ParameterValidationException("콘서트 ID는 0보다 커야 합니다: $concertId")
            }
            if (concertDate.isBefore(LocalDate.now())) {
                throw ParameterValidationException("콘서트 날짜는 현재 날짜 이후여야 합니다")
            }
            if (venue.isBlank()) {
                throw ParameterValidationException("공연장 이름은 필수입니다")
            }
            if (totalSeats <= 0) {
                throw ParameterValidationException("총 좌석 수는 0보다 커야 합니다: $totalSeats")
            }
            
            return ConcertSchedule(
                concertId = concertId,
                concertDate = concertDate,
                venue = venue,
                totalSeats = totalSeats,
                availableSeats = totalSeats // 초기에는 모든 좌석이 예약 가능
            )
        }
    }
    
    fun reserveSeat(): ConcertSchedule {
        if (availableSeats <= 0) {
            throw IllegalStateException("예약 가능한 좌석이 없습니다")
        }
        
        return this.copy(
            availableSeats = availableSeats - 1
        )
    }
    
    fun releaseSeat(): ConcertSchedule {
        if (availableSeats >= totalSeats) {
            throw IllegalStateException("이미 모든 좌석이 예약 가능한 상태입니다")
        }
        
        return this.copy(
            availableSeats = availableSeats + 1
        )
    }
    
    fun isBookingAvailable(): Boolean {
        return concertDate.isAfter(LocalDate.now()) && availableSeats > 0
    }
    
    fun isFullyBooked(): Boolean {
        return availableSeats == 0
    }
    
    fun getBookingRate(): Double {
        return if (totalSeats == 0) 0.0 
               else (totalSeats - availableSeats).toDouble() / totalSeats.toDouble()
    }
}
