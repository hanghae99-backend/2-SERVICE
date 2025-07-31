package kr.hhplus.be.server.domain.concert.models

import kr.hhplus.be.server.global.common.BaseEntity

import kr.hhplus.be.server.global.exception.ParameterValidationException
import kr.hhplus.be.server.domain.reservation.model.Reservation
import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "concert_schedule",
    indexes = [
        Index(name = "idx_concert_schedule_concert_id_concert_date", columnList = "concert_id, concert_date"),
        Index(name = "idx_concert_schedule_available_seats", columnList = "available_seats")
    ]
)
class ConcertSchedule(
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
    var availableSeats: Int
) : BaseEntity() {
    
    // ConcertSchedule -> Concert 연관관계 (N:1)
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
                availableSeats = totalSeats
            )
        }
    }
    
    fun reserveSeat() {
        if (availableSeats <= 0) {
            throw IllegalStateException("예약 가능한 좌석이 없습니다")
        }
        availableSeats -= 1
    }
    
    fun releaseSeat() {
        if (availableSeats >= totalSeats) {
            throw IllegalStateException("이미 모든 좌석이 예약 가능한 상태입니다")
        }
        availableSeats += 1
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
