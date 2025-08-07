package kr.hhplus.be.server.domain.concert.repositories

import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import java.time.LocalDate

interface ConcertScheduleRepository {
    fun save(concertSchedule: ConcertSchedule): ConcertSchedule
    fun findById(id: Long): ConcertSchedule?
    fun findByConcertId(concertId: Long): List<ConcertSchedule>
    fun findByConcertDate(concertDate: LocalDate): List<ConcertSchedule>
    fun findByConcertDateBetween(startDate: LocalDate, endDate: LocalDate): List<ConcertSchedule>
    fun findByAvailableSeatsGreaterThan(availableSeats: Int): List<ConcertSchedule>
    fun findByConcertDateBetweenAndAvailableSeatsGreaterThanOrderByConcertDateAsc(
        startDate: LocalDate, 
        endDate: LocalDate, 
        availableSeats: Int
    ): List<ConcertSchedule>
    fun findByConcertIdAndAvailableSeatsGreaterThanAndConcertDateGreaterThanEqualOrderByConcertDateAsc(
        concertId: Long, 
        availableSeats: Int, 
        currentDate: LocalDate
    ): List<ConcertSchedule>
    fun findByConcertIdIn(concertIds: List<Long>): List<ConcertSchedule>
    fun findAllByOrderByConcertDateAsc(): List<ConcertSchedule>
    fun findByConcertDateGreaterThanEqualOrderByConcertDateAsc(concertDate: LocalDate): List<ConcertSchedule>
    fun delete(concertSchedule: ConcertSchedule)
    fun deleteAll() // 테스트용 - 모든 콘서트 스케줄 데이터 삭제
}
