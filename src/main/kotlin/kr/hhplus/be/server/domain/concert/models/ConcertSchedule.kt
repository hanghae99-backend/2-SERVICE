package kr.hhplus.be.server.domain.concert.models

import kr.hhplus.be.server.global.common.BaseEntity
import kr.hhplus.be.server.global.exception.ParameterValidationException
import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "concert_schedule",
    indexes = [
        Index(name = "idx_concert_schedule_concert_id_date", columnList = "concert_id, concert_date"),
        Index(name = "idx_concert_schedule_date_available", columnList = "concert_date, available_seats"),
        Index(name = "idx_concert_schedule_venue", columnList = "venue"),
        Index(name = "idx_concert_schedule_booking_status", columnList = "concert_date, available_seats, total_seats")
    ]
)
class ConcertSchedule(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var scheduleId: Long = 0,
    
    @Column(name = "concert_id", nullable = false)
    var concertId: Long,
    
    @Column(name = "concert_date", nullable = false)
    var concertDate: LocalDate,
    
    @Column(name = "venue", nullable = false, length = 200)
    var venue: String,
    
    @Column(name = "total_seats", nullable = false)
    var totalSeats: Int,
    
    @Column(name = "available_seats", nullable = false)
    var availableSeats: Int,
    
    @Column(name = "reservation_starts_at", nullable = true)
    var reservationStartsAt: LocalDateTime? = null,
    
    @Column(name = "reservation_ends_at", nullable = true)
    var reservationEndsAt: LocalDateTime? = null
) : BaseEntity() {
    
    companion object {
        fun create(
            concertId: Long,
            concertDate: LocalDate,
            venue: String,
            totalSeats: Int,
            reservationStartsAt: LocalDateTime? = null,
            reservationEndsAt: LocalDateTime? = null
        ): ConcertSchedule {
            validateCreateParameters(concertId, concertDate, venue, totalSeats)
            
            return ConcertSchedule(
                concertId = concertId,
                concertDate = concertDate,
                venue = venue.trim(),
                totalSeats = totalSeats,
                availableSeats = totalSeats,
                reservationStartsAt = reservationStartsAt,
                reservationEndsAt = reservationEndsAt ?: concertDate.atTime(23, 59)
            )
        }
        
        private fun validateCreateParameters(
            concertId: Long,
            concertDate: LocalDate,
            venue: String,
            totalSeats: Int
        ) {
            if (concertId <= 0) {
                throw ParameterValidationException("콘서트 ID는 0보다 커야 합니다: $concertId")
            }
            if (concertDate.isBefore(LocalDate.now().plusDays(1))) {
                throw ParameterValidationException("콘서트 날짜는 최소 하루 후부터 등록 가능합니다")
            }
            if (venue.isBlank()) {
                throw ParameterValidationException("공연장 이름은 필수입니다")
            }
            if (totalSeats <= 0) {
                throw ParameterValidationException("총 좌석 수는 0보다 커야 합니다: $totalSeats")
            }
        }
    }
    
    fun reserveSeat(count: Int = 1) {
        validateSeatReservation(count)
        availableSeats -= count
    }
    
    fun releaseSeat(count: Int = 1) {
        validateSeatRelease(count)
        availableSeats += count
    }
    
    fun updateReservationPeriod(startsAt: LocalDateTime?, endsAt: LocalDateTime?) {
        validateReservationPeriod(startsAt, endsAt)
        this.reservationStartsAt = startsAt
        this.reservationEndsAt = endsAt
    }
    
    fun extendReservationDeadline(hours: Long) {
        reservationEndsAt = reservationEndsAt?.plusHours(hours)
            ?: LocalDateTime.now().plusHours(hours)
    }
    
    private fun validateSeatReservation(count: Int) {
        if (count <= 0) {
            throw IllegalArgumentException("예약 좌석 수는 0보다 커야 합니다: $count")
        }
        if (availableSeats < count) {
            throw IllegalStateException("예약 가능한 좌석이 부족합니다. 요청: $count, 가능: $availableSeats")
        }
        if (!isReservationPeriod()) {
            throw IllegalStateException("예약 가능 기간이 아닙니다")
        }
    }
    
    private fun validateSeatRelease(count: Int) {
        if (count <= 0) {
            throw IllegalArgumentException("해제 좌석 수는 0보다 커야 합니다: $count")
        }
        if (availableSeats + count > totalSeats) {
            throw IllegalStateException("전체 좌석 수를 초과할 수 없습니다. 현재: $availableSeats, 해제: $count, 전체: $totalSeats")
        }
    }
    
    private fun validateReservationPeriod(startsAt: LocalDateTime?, endsAt: LocalDateTime?) {
        if (startsAt != null && endsAt != null) {
            if (startsAt.isAfter(endsAt)) {
                throw IllegalArgumentException("예약 시작 시간이 종료 시간보다 늦을 수 없습니다")
            }
            if (endsAt.isAfter(concertDate.atTime(23, 59))) {
                throw IllegalArgumentException("예약 종료 시간은 콘서트 날짜를 초과할 수 없습니다")
            }
        }
    }
    
    fun isBookingAvailable(): Boolean {
        return concertDate.isAfter(LocalDate.now()) 
                && availableSeats > 0 
                && isReservationPeriod()
    }
    
    fun isReservationPeriod(): Boolean {
        val now = LocalDateTime.now()
        val startTime = reservationStartsAt ?: return true
        val endTime = reservationEndsAt ?: concertDate.atTime(23, 59)
        
        return now.isAfter(startTime) && now.isBefore(endTime)
    }
    
    fun isFullyBooked(): Boolean = availableSeats == 0
    
    fun isSoldOut(): Boolean = isFullyBooked()
    
    fun isPastConcert(): Boolean = concertDate.isBefore(LocalDate.now())
    
    fun isUpcoming(): Boolean = concertDate.isAfter(LocalDate.now())
    
    fun getBookingRate(): Double {
        return if (totalSeats == 0) 0.0 
               else (totalSeats - availableSeats).toDouble() / totalSeats.toDouble()
    }
    
    fun getAvailabilityStatus(): ScheduleStatus {
        return when {
            isPastConcert() -> ScheduleStatus.PAST
            isFullyBooked() -> ScheduleStatus.SOLD_OUT
            !isReservationPeriod() -> ScheduleStatus.RESERVATION_CLOSED
            availableSeats < totalSeats * 0.1 -> ScheduleStatus.ALMOST_SOLD_OUT
            else -> ScheduleStatus.AVAILABLE
        }
    }
    
    fun getRemainingDays(): Long {
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), concertDate)
    }
}

enum class ScheduleStatus {
    AVAILABLE,
    ALMOST_SOLD_OUT,
    SOLD_OUT,
    RESERVATION_CLOSED,
    PAST
}
