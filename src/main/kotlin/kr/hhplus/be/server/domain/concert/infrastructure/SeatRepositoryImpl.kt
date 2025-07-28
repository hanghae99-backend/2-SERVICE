package kr.hhplus.be.server.domain.concert.infrastructure

import kr.hhplus.be.server.domain.concert.models.Seat
import kr.hhplus.be.server.domain.concert.repositories.SeatRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal

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
    
    override fun findByScheduleId(scheduleId: Long): List<Seat> {
        return seatJpaRepository.findByScheduleId(scheduleId)
    }
    
    override fun findByScheduleIdAndStatusCode(scheduleId: Long, statusCode: String): List<Seat> {
        return seatJpaRepository.findByScheduleIdAndStatusCode(scheduleId, statusCode)
    }
    
    override fun findByScheduleIdAndSeatNumber(scheduleId: Long, seatNumber: String): Seat? {
        return seatJpaRepository.findByScheduleIdAndSeatNumber(scheduleId, seatNumber)
    }
    
    override fun findByScheduleIdAndStatusCodeOrderBySeatNumberAsc(scheduleId: Long, statusCode: String): List<Seat> {
        return seatJpaRepository.findByScheduleIdAndStatusCodeOrderBySeatNumberAsc(scheduleId, statusCode)
    }
    
    override fun countByScheduleId(scheduleId: Long): Int {
        return seatJpaRepository.countByScheduleId(scheduleId)
    }
    
    override fun countByScheduleIdAndStatusCode(scheduleId: Long, statusCode: String): Int {
        return seatJpaRepository.countByScheduleIdAndStatusCode(scheduleId, statusCode)
    }
    
    override fun findByScheduleIdAndStatusCodeAndPriceBetweenOrderByPriceAscSeatNumberAsc(
        scheduleId: Long, 
        statusCode: String, 
        minPrice: BigDecimal, 
        maxPrice: BigDecimal
    ): List<Seat> {
        return seatJpaRepository.findByScheduleIdAndStatusCodeAndPriceBetweenOrderByPriceAscSeatNumberAsc(
            scheduleId, statusCode, minPrice, maxPrice
        )
    }
    
    override fun findByScheduleIdAndSeatNumberContainingOrderBySeatNumberAsc(scheduleId: Long, seatNumberPattern: String): List<Seat> {
        return seatJpaRepository.findByScheduleIdAndSeatNumberContainingOrderBySeatNumberAsc(scheduleId, seatNumberPattern)
    }
    
    override fun findByScheduleIdAndStatusCodeAndPriceLessThanEqualOrderByPriceAsc(
        scheduleId: Long, 
        statusCode: String, 
        maxPrice: BigDecimal
    ): List<Seat> {
        return seatJpaRepository.findByScheduleIdAndStatusCodeAndPriceLessThanEqualOrderByPriceAsc(
            scheduleId, statusCode, maxPrice
        )
    }
    
    override fun delete(seat: Seat) {
        seatJpaRepository.delete(seat)
    }
}
