package kr.hhplus.be.server.domain.concert.repositories

import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import java.time.LocalDate

interface ConcertScheduleRepository {
    fun save(schedule: ConcertSchedule): ConcertSchedule
    fun findById(id: Long): ConcertSchedule?
    fun findByConcertId(concertId: Long): List<ConcertSchedule>
    fun findByConcertIdAndAvailableSeatsGreaterThan(concertId: Long, minSeats: Int): List<ConcertSchedule>
    fun findByConcertDateBetween(startDate: LocalDate, endDate: LocalDate): List<ConcertSchedule>
    fun findByConcertDateBetweenAndAvailableSeatsGreaterThanOrderByConcertDateAsc(
        startDate: LocalDate, 
        endDate: LocalDate, 
        availableSeats: Int
    ): List<ConcertSchedule> // 추가
    fun findAll(): List<ConcertSchedule>
    fun delete(schedule: ConcertSchedule)
    fun deleteAll()
    fun flush() // 추가
}
