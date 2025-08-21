package kr.hhplus.be.server.domain.concert.infrastructure

import kr.hhplus.be.server.domain.concert.models.Seat
import kr.hhplus.be.server.domain.concert.repositories.SeatRepository
import org.springframework.stereotype.Repository

@Repository
class SeatRepositoryImpl(
    private val seatJpaRepository: SeatJpaRepository
) : SeatRepository {
    
    override fun save(seat: Seat): Seat {
        return seatJpaRepository.save(seat)
    }
    
    override fun saveAll(seats: List<Seat>): List<Seat> {
        return seatJpaRepository.saveAll(seats)
    }
    
    override fun findById(id: Long): Seat? {
        return seatJpaRepository.findById(id).orElse(null)
    }
    
    override fun findByIdWithPessimisticLock(id: Long): Seat? {
        return seatJpaRepository.findByIdWithPessimisticLock(id).orElse(null)
    }
    
    override fun findByScheduleId(scheduleId: Long): List<Seat> {
        return seatJpaRepository.findByScheduleId(scheduleId)
    }
    
    override fun findByScheduleIdAndStatusCode(scheduleId: Long, statusCode: String): List<Seat> {
        return seatJpaRepository.findByScheduleIdAndStatusCode(scheduleId, statusCode)
    }
    
    override fun findByScheduleIdAndStatusCodeOrderBySeatNumberAsc(scheduleId: Long, statusCode: String): List<Seat> {
        return seatJpaRepository.findByScheduleIdAndStatusCodeOrderBySeatNumberAsc(scheduleId, statusCode)
    }
    
    override fun countByScheduleId(scheduleId: Long): Int {
        return seatJpaRepository.countByScheduleId(scheduleId)
    }
    
    override fun findAll(): List<Seat> {
        return seatJpaRepository.findAll()
    }
    
    override fun delete(seat: Seat) {
        seatJpaRepository.delete(seat)
    }
    
    override fun deleteAll() {
        seatJpaRepository.deleteAll()
    }
    
    override fun flush() {
        seatJpaRepository.flush()
    }
}
