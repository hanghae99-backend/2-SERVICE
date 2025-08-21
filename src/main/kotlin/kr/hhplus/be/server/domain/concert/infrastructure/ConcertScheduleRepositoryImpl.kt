package kr.hhplus.be.server.domain.concert.infrastructure

import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import kr.hhplus.be.server.domain.concert.repositories.ConcertScheduleRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class ConcertScheduleRepositoryImpl(
    private val concertScheduleJpaRepository: ConcertScheduleJpaRepository
) : ConcertScheduleRepository {

    override fun save(schedule: ConcertSchedule): ConcertSchedule {
        return concertScheduleJpaRepository.save(schedule)
    }

    override fun findById(id: Long): ConcertSchedule? {
        return concertScheduleJpaRepository.findById(id).orElse(null)
    }

    override fun findByConcertId(concertId: Long): List<ConcertSchedule> {
        return concertScheduleJpaRepository.findByConcertId(concertId)
    }
    
    override fun findByConcertIdAndAvailableSeatsGreaterThan(concertId: Long, minSeats: Int): List<ConcertSchedule> {
        return concertScheduleJpaRepository.findByConcertIdAndAvailableSeatsGreaterThan(concertId, minSeats)
    }

    override fun findByConcertDateBetween(startDate: LocalDate, endDate: LocalDate): List<ConcertSchedule> {
        return concertScheduleJpaRepository.findByConcertDateBetween(startDate, endDate)
    }
    
    override fun findByConcertDateBetweenAndAvailableSeatsGreaterThanOrderByConcertDateAsc(
        startDate: LocalDate, 
        endDate: LocalDate, 
        availableSeats: Int
    ): List<ConcertSchedule> {
        return concertScheduleJpaRepository.findByConcertDateBetweenAndAvailableSeatsGreaterThanOrderByConcertDateAsc(
            startDate, endDate, availableSeats
        )
    }
    
    override fun findAll(): List<ConcertSchedule> {
        return concertScheduleJpaRepository.findAll()
    }

    override fun delete(schedule: ConcertSchedule) {
        concertScheduleJpaRepository.delete(schedule)
    }
    
    override fun deleteAll() {
        concertScheduleJpaRepository.deleteAll()
    }
    
    override fun flush() {
        concertScheduleJpaRepository.flush()
    }
    
    override fun findByConcertIdIn(concertIds: List<Long>): List<ConcertSchedule> {
        return concertScheduleJpaRepository.findByConcertIdIn(concertIds)
    }
}
