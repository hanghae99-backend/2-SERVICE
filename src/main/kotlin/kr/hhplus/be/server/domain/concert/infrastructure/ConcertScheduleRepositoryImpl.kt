package kr.hhplus.be.server.domain.concert.infrastructure

import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import kr.hhplus.be.server.domain.concert.repositories.ConcertScheduleRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class ConcertScheduleRepositoryImpl(
    private val concertScheduleJpaRepository: ConcertScheduleJpaRepository
) : ConcertScheduleRepository {

    override fun save(concertSchedule: ConcertSchedule): ConcertSchedule {
        return concertScheduleJpaRepository.save(concertSchedule)
    }

    override fun findById(id: Long): ConcertSchedule? {
        return concertScheduleJpaRepository.findById(id).orElse(null)
    }

    override fun findByConcertId(concertId: Long): List<ConcertSchedule> {
        return concertScheduleJpaRepository.findByConcertId(concertId)
    }

    override fun findByConcertDate(concertDate: LocalDate): List<ConcertSchedule> {
        return concertScheduleJpaRepository.findByConcertDate(concertDate)
    }

    override fun findByConcertDateBetween(startDate: LocalDate, endDate: LocalDate): List<ConcertSchedule> {
        return concertScheduleJpaRepository.findByConcertDateBetween(startDate, endDate)
    }

    override fun findByAvailableSeatsGreaterThan(availableSeats: Int): List<ConcertSchedule> {
        return concertScheduleJpaRepository.findByAvailableSeatsGreaterThan(availableSeats)
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

    override fun findByConcertIdAndAvailableSeatsGreaterThanAndConcertDateGreaterThanEqualOrderByConcertDateAsc(
        concertId: Long,
        availableSeats: Int,
        currentDate: LocalDate
    ): List<ConcertSchedule> {
        return concertScheduleJpaRepository.findByConcertIdAndAvailableSeatsGreaterThanAndConcertDateGreaterThanEqualOrderByConcertDateAsc(
            concertId, availableSeats, currentDate
        )
    }

    override fun findByConcertIdIn(concertIds: List<Long>): List<ConcertSchedule> {
        return concertScheduleJpaRepository.findByConcertIdIn(concertIds)
    }

    override fun findAllByOrderByConcertDateAsc(): List<ConcertSchedule> {
        return concertScheduleJpaRepository.findAllByOrderByConcertDateAsc()
    }

    override fun findByConcertDateGreaterThanEqualOrderByConcertDateAsc(concertDate: LocalDate): List<ConcertSchedule> {
        return concertScheduleJpaRepository.findByConcertDateGreaterThanEqualOrderByConcertDateAsc(concertDate)
    }

    override fun delete(concertSchedule: ConcertSchedule) {
        concertScheduleJpaRepository.delete(concertSchedule)
    }
}
