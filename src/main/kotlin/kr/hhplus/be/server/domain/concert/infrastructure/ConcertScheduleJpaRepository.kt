package kr.hhplus.be.server.domain.concert.infrastructure

import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface ConcertScheduleJpaRepository : JpaRepository<ConcertSchedule, Long> {
    
    // 콘서트 ID로 스케줄 조회
    fun findByConcertId(concertId: Long): List<ConcertSchedule>
    
    // 특정 날짜의 스케줄 조회
    fun findByConcertDate(concertDate: LocalDate): List<ConcertSchedule>
    
    // 날짜 범위로 스케줄 조회
    fun findByConcertDateBetween(startDate: LocalDate, endDate: LocalDate): List<ConcertSchedule>
    
    // 예약 가능한 스케줄 조회 (예약 가능 좌석이 있는 것)
    fun findByAvailableSeatsGreaterThan(availableSeats: Int): List<ConcertSchedule>
    
    // 날짜 범위 + 예약 가능한 스케줄 조회
    fun findByConcertDateBetweenAndAvailableSeatsGreaterThanOrderByConcertDateAsc(
        startDate: LocalDate, 
        endDate: LocalDate, 
        availableSeats: Int
    ): List<ConcertSchedule>
    
    // 특정 콘서트의 예약 가능한 스케줄 조회
    fun findByConcertIdAndAvailableSeatsGreaterThanAndConcertDateGreaterThanEqualOrderByConcertDateAsc(
        concertId: Long, 
        availableSeats: Int, 
        currentDate: LocalDate
    ): List<ConcertSchedule>
    
    // 여러 콘서트 ID로 스케줄 조회
    fun findByConcertIdIn(concertIds: List<Long>): List<ConcertSchedule>
    
    // 콘서트 날짜 순 정렬 조회
    fun findAllByOrderByConcertDateAsc(): List<ConcertSchedule>
    
    // 특정 날짜 이후의 스케줄 조회
    fun findByConcertDateGreaterThanEqualOrderByConcertDateAsc(concertDate: LocalDate): List<ConcertSchedule>
}
